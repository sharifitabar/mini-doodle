#!/bin/sh
# loadtest/run.sh — orchestrate the local k6 load test against the 1000-user booking dataset.
#
# Usage:
#   ./loadtest/run.sh [--no-reset] [--no-build] [--smoke] [--testid ID] [--help]
#
# Behaviour (feature 007):
#   1. docker compose up -d [--build] (brings db/app/prometheus/grafana/postgres-exporter up)
#   2. Poll http://localhost:8080/actuator/health for UP with a 180 s timeout (readiness)
#   3. Reset + seed + verify the dataset (unless --no-reset). --smoke scales down SEED_USERS/LOAD_VUS.
#   4. Derive WINDOW_FROM/WINDOW_TO from the seeded slots (past history) so the availability read
#      window covers the whole seeded history (≈25 days, inside MAX_SPAN=31d)
#   5. Run k6 with TEST_ID and the resolved window; propagate k6's exit code unchanged
#   6. Print the Grafana URL including var-testid
#
# The manipulation flow writes into the future; verify.sql enforces "future is free" (I7). If a
# previous run leaked future slots, the run stops before load begins. This is intentional (D13).

set -eu

usage() {
  cat <<'USAGE'
Usage: ./loadtest/run.sh [options]

Options:
  --no-reset   Skip reset + seed + verify (assume dataset already correct). If you use this for
               back-to-back runs, you accept responsibility for a clean slate.
  --no-build   Do not rebuild the application image when bringing the stack up.
  --smoke      Use a small dataset (SEED_USERS=10, LOAD_VUS=10, ~30s steady) to prove plumbing
               only — not performance. Mix thresholds are relaxed in smoke.
  --testid ID  Set TEST_ID explicitly; otherwise a UTC timestamp is generated.
  -h, --help   Show this help and exit.

Examples:
  ./loadtest/run.sh                # full run (1000 users)
  ./loadtest/run.sh --smoke        # wiring check in ~1 minute
  ./loadtest/run.sh --no-reset     # continue an already-provisioned dataset
USAGE
}

NO_RESET=0
NO_BUILD=0
SMOKE=0
TEST_ID=""

# Split user-provided TEST_ID? --testid ID takes two args
while [ $# -gt 0 ]; do
  case "$1" in
    --no-reset)
      NO_RESET=1
      ;;
    --no-build)
      NO_BUILD=1
      ;;
    --smoke)
      SMOKE=1
      ;;
    --testid)
      shift
      if [ $# -eq 0 ]; then
        echo "error: --testid requires an ID" >&2
        usage >&2
        exit 2
      fi
      TEST_ID="$1"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "error: unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
  shift
done

# Map host user to the container user for the k6 service (results written back to host). Most
# systems (Linux/macOS) have a writable results dir as long as the uid/gid match.
export LOADTEST_UID="$(id -u)"
export LOADTEST_GID="$(id -g)"

# Host URL for readiness polling (curl is not in the app image; we poll from the host).
BASE_URL_HOST="${LOADTEST_HOST_URL:-http://localhost:8080}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"

echo "==> Bringing the stack up"
if [ "$NO_BUILD" = 1 ]; then
  docker compose up -d
else
  docker compose up -d --build
fi

echo "==> Waiting for service readiness (${BASE_URL_HOST}/actuator/health)..."
i=0
while true; do
  i=$((i + 1))
  # Use curl if available; fall back to wget if curl is missing on the host.
  if command -v curl >/dev/null 2>&1; then
    if curl -fsS "${BASE_URL_HOST}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      break
    fi
  elif command -v wget >/dev/null 2>&1; then
    if wget -qO- "${BASE_URL_HOST}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      break
    fi
  else
    echo "error: neither curl nor wget is available on the host for readiness polling" >&2
    exit 1
  fi
  if [ "$i" -gt 90 ]; then
    echo "error: service did not become healthy within 180 s" >&2
    exit 1
  fi
  sleep 2
done
echo "    service is UP"

# Dataset sizing and smoke flags. These are exported so docker compose interpolates them into
# the k6/seed services' environment, and so run.sh can reason about them.
if [ "$SMOKE" = 1 ]; then
  export SEED_USERS=10
  export LOAD_VUS=10
  export LOAD_STEADY_SECONDS=30
  export LOAD_RAMP_SECONDS=10
  export LOAD_RAMPDOWN_SECONDS=10
  export SMOKE=1
  echo "==> Smoke mode: SEED_USERS=${SEED_USERS} LOAD_VUS=${LOAD_VUS} steady=${LOAD_STEADY_SECONDS}s"
else
  export SEED_USERS="${SEED_USERS:-1000}"
  export LOAD_VUS="${LOAD_VUS:-1000}"
  export LOAD_RAMP_SECONDS="${LOAD_RAMP_SECONDS:-120}"
  export LOAD_STEADY_SECONDS="${LOAD_STEADY_SECONDS:-600}"
  export LOAD_RAMPDOWN_SECONDS="${LOAD_RAMPDOWN_SECONDS:-30}"
  export SMOKE=0
  echo "==> Normal mode: SEED_USERS=${SEED_USERS} LOAD_VUS=${LOAD_VUS} steady=${LOAD_STEADY_SECONDS}s"
fi

# Enforce the guard early, before k6 starts (mirror the in-script guard for clarity)
if [ "$LOAD_VUS" -gt "$SEED_USERS" ] 2>/dev/null; then
  echo "error: LOAD_VUS (${LOAD_VUS}) > SEED_USERS (${SEED_USERS}) — a VU would map to a non-existent user identity" >&2
  exit 2
fi

if [ "$NO_RESET" = 0 ]; then
  echo "==> Resetting dataset (load block) and re-seeding..."
  docker compose --profile loadtest run --rm seed /sql/reset.sql
  docker compose --profile loadtest run --rm seed
  echo "==> Verifying invariants..."
  docker compose --profile loadtest run --rm seed /sql/verify.sql
  echo "    dataset verified"
else
  echo "==> Skipping reset/seed/verify (--no-reset)"
  echo "==> Verifying invariants (still enforced if scripts exist)..."
  if docker compose --profile loadtest run --rm seed /sql/verify.sql >/dev/null 2>&1; then
    echo "    invariants hold"
  else
    echo "warning: verify.sql failed or is unavailable; proceeding (--no-reset) is at your risk" >&2
  fi
fi

# Resolve the read window from seeded slots: [min(start_at) in UTC, max(start_at)+1h in UTC] -> Zulu
# Must explicitly format as YYYY-MM-DDTHH:MM:SSZ because psql's default timestamptz string includes
# a space and offset (e.g. 2026-09-23 10:00:00+00), which Spring rejects.
echo "==> Resolving availability window from seeded data..."
WINDOW_RAW=$(
  docker compose exec -T db psql -U minidoodle -d minidoodle -tA -F'|' -c "
    SELECT
      to_char(min(s.start_at) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS') || 'Z',
      to_char(max(s.start_at) AT TIME ZONE 'UTC' + interval '1 hour', 'YYYY-MM-DD\"T\"HH24:MI:SS') || 'Z'
    FROM time_slots s
    JOIN calendars k ON k.id = s.calendar_id
    JOIN users u ON u.id = k.user_id
    WHERE u.id >= '10000000-0000-4000-8000-000000000000'
      AND u.id < '10000000-0000-4000-8001-000000000000'
      AND s.start_at < now()
  "
)
export WINDOW_FROM="${WINDOW_RAW%%|*}"
export WINDOW_TO="${WINDOW_RAW##*|}"
if [ -z "${WINDOW_FROM}" ] || [ -z "${WINDOW_TO}" ]; then
  echo "error: failed to resolve WINDOW_FROM/WINDOW_TO from the database" >&2
  exit 1
fi
echo "    WINDOW_FROM=${WINDOW_FROM}  WINDOW_TO=${WINDOW_TO}"

if [ -z "$TEST_ID" ]; then
  TEST_ID="$(date -u +%Y%m%dT%H%M%SZ)"
fi
export TEST_ID

echo "==> Running k6 scenario (testid=${TEST_ID})"
set +e
docker compose --profile loadtest run --rm k6
K6_EXIT=$?
set -e

echo "==> Grafana: ${GRAFANA_URL}/d/minidoodle-load-test/?var-testid=${TEST_ID}"
echo "==> Summary artifact: loadtest/results/summary-${TEST_ID}.json (if written)"

exit "$K6_EXIT"
