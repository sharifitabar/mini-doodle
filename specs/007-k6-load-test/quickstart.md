# Quickstart: Running the Load Test

**Feature**: `007-k6-load-test` | **Date**: 2026-10-18

## What this answers

> At 1000 concurrent users, each holding 600–800 slots, with 5% of activity being *add a slot →
> convert it to a meeting* and 95% being availability checks — how slow does the service get, where
> does it hurt, and does it stay inside the agreed latency and error targets?

It answers that **locally**, on the existing compose stack, and it leaves the evidence in Grafana.

## Prerequisites

| Requirement | Notes |
| --- | --- |
| Docker Desktop (or Docker Engine + Compose v2) | The whole stack, the generator and the seeder run in containers |
| **≥ 8 GB** assigned to the Docker VM (10 GB comfortable) | 1000 resident k6 VU runtimes plus the app, Postgres, Prometheus and Grafana |
| **≥ 5 GB** free disk | ~0.5 GB dataset per seeded state, plus Prometheus TSDB growth |
| `curl` and `sh` on the host | Used only for readiness polling and orchestration |
| k6 installed on the host? | **Not required.** The generator runs as `grafana/k6:2.3.0` |

## Run it

```bash
./loadtest/run.sh
```

That single command is the whole procedure: brings the stack up, waits for the service to be
healthy, resets and reseeds the dataset, resolves the read window from the database, runs the
scenario, prints the Grafana URL, and exits with k6's verdict code.

Useful variants:

```bash
./loadtest/run.sh --smoke            # 10 users, 10 VUs, ~30s: check the wiring, not performance
./loadtest/run.sh --testid before    # name the run, so two runs are easy to compare in Grafana
./loadtest/run.sh --no-build         # reuse already-built images
./loadtest/run.sh --no-reset         # continue on the existing dataset (see the caveat below)
./loadtest/reset.sh                  # just remove the load dataset
```

Reset-and-reseed is the **default** on purpose: the manipulation flow writes its new slots into the
empty future on a deterministic grid, so a second run against a dataset left over from the first
would attempt the same hours, be refused with `409` every time, and measure a write path that never
wrote anything. `--no-reset` is for deliberate continuation experiments; the run's `checks`
threshold is the backstop that catches it if you forget.

If you prefer to see every step, the same procedure by hand:

```bash
docker compose up -d --build
curl -fsS http://localhost:8080/actuator/health                 # wait for "status":"UP"
docker compose --profile loadtest run --rm seed /sql/reset.sql   # remove any previous dataset
docker compose --profile loadtest run --rm seed                  # seed; idempotent
docker compose exec -T db psql -U minidoodle -d minidoodle -tA -F'|' -c \
  "SELECT
      to_char(min(s.start_at) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS') || 'Z',
      to_char(max(s.start_at) AT TIME ZONE 'UTC' + interval '1 hour', 'YYYY-MM-DD\"T\"HH24:MI:SS') || 'Z'
    FROM time_slots s
    JOIN calendars k ON k.id = s.calendar_id
    JOIN users u ON u.id = k.user_id
    WHERE u.id >= '10000000-0000-4000-8000-000000000000'
      AND u.id < '10000000-0000-4000-8001-000000000000'
      AND s.start_at < now()"
# copy the two timestamps into WINDOW_FROM / WINDOW_TO, then (the image entrypoint is already k6):
docker compose --profile loadtest run --rm \
  -e TEST_ID=manual -e WINDOW_FROM=... -e WINDOW_TO=... \
  k6 run -o experimental-prometheus-rw /scripts/booking.js
```

## What happens, and how long it takes

| Step | Typical wall clock | Notes |
| --- | --- | --- |
| Stack up (build on first run) | 1–5 min first time, ~20 s after | Image build dominates the first run |
| Readiness wait | seconds | Polls `/actuator/health` until `UP` |
| Seed | seconds – 2 min | One pass inserts ≈851,000 rows and runs `ANALYZE` |
| Load | ~6 min | 30 s ramp-up + 5 min steady state + 30 s ramp-down (defaults; override via env) |
| Total | ~8 min (first run longer if rebuilding) | Scale back up with `SEED_USERS`/`LOAD_VUS`/`LOAD_STEADY_SECONDS` |

## Where to look while it runs

| Surface | URL |
| --- | --- |
| Grafana (the run) | <http://localhost:3000/d/minidoodle-load-test> — pick your `testid` in the top variable |
| Service-side view | same dashboard, "Service side" row |
| Database pressure | same dashboard, "Database pressure" row |
| Prometheus (raw) | <http://localhost:9090> — `k6_http_req_duration_p95{testid="..."}` |
| Prometheus targets | <http://localhost:9090/targets> — `minidoodle` and `postgres` must both be **UP** |
| Summary artifact | `loadtest/results/summary-<TEST_ID>.json` after the run |

The generator's own view and the service/database view share the dashboard's clock, so you can read
"the service slowed down at 14:32" next to "Postgres had 10 active sessions and N lock waits at
14:32" in one glance.

## Reading the verdict

The run passes only if **all** of these hold (spec FR-026):

| Threshold | Target |
| --- | --- |
| Availability checks, client-observed | p95 < **300 ms**, p99 < **1 s** |
| Manipulation sequence (add slot → book meeting) | p95 < **500 ms** |
| Error rate (`http_req_failed`, expected `409` refusals excluded) | < **1%** |
| Measured mix | manipulations between **4% and 6%** |
| Checks | > **99%** |

Exit codes: `0` = all thresholds held · `99` = at least one breached (the automated fail signal) ·
anything else = k6 could not run the scenario.

## Expect the first run to be honest, not pretty

This is a **measurement**, not a tuning exercise, and the current configuration is untuned for this
scale:

- HikariCP is at Spring Boot's default of **10 connections**; Postgres `max_connections` is the
  default 100. At 1000 resident users, pool queueing is a likely finding.
- **A failing run is still a successful outcome of this feature.** It is the signal the feature
  exists to produce. Fixing whatever it exposes (pool sizing, indexes, caching) is a separate,
  deliberate change — the load test's thresholds stay where the spec put them.

To tell the three failure modes apart:

| Observation | Reading |
| --- | --- |
| `k6_vus` = 1000, latency high, Postgres backends ≤ 10 with growing active/waiting sessions | **Connection-pool bound** — the service is waiting for a connection |
| Postgres active sessions high, blocks-read high, service-side p95 rising with client p95 | **Database bound** — the queries themselves are the cost |
| Host CPU pinned, k6's own iteration duration rising, latency high while the service-side p95 stays flat | **Generator bound** — the laptop, not the service; lower `LOAD_VUS` and re-run before drawing conclusions |

## Repeat runs

The seed is idempotent, so a second run reuses the same dataset and stays comparable. Over many
runs the delete/insert cycles accumulate dead tuples; when numbers start drifting without a code
change, take the clean-slate path:

```bash
docker compose down -v        # also clears Prometheus and Grafana volumes
./loadtest/run.sh
```

`down -v` also destroys previous runs' Grafana history, which is why it is not the default reset.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| 403s in the run, `checks` red | The dataset is missing or the generator's user IDs don't match the seeded namespace | Run the seed step; confirm `select count(*) from users where id >= '10000000-0000-4000-8000-000000000000' and id < '10000000-0000-4000-8001-000000000000'` = 1000. The wider `10000000`–`20000000` range also contains the demo user |
| 400s from `/api/v1/availability` | `WINDOW_FROM`/`WINDOW_TO` not in ISO-8601 (`psql`'s default timestamp format is not accepted) | Use `run.sh`, or the `to_char(...)` query above |
| 400s from `POST /api/v1/slots` | Generated `startAt` is not in the future (host clock skew) | Check the host clock; the generator derives times from `now` |
| Grafana panels empty but the run clearly happened | Prometheus missing `--web.enable-remote-write-receiver`, or the stack wasn't recreated after adding it | `docker compose up -d --force-recreate prometheus`; check `docker compose logs prometheus` for remote-write errors |
| `postgres` target DOWN | Exporter not running or wrong `DATA_SOURCE_NAME` | `docker compose ps`; `docker compose logs postgres-exporter` |
| VUs never reach 1000, or the container is OOM-killed | Docker VM memory too small for 1000 VU runtimes | Raise Docker Desktop memory, or run with a lower `LOAD_VUS` and say so when you report numbers |
| Port already in use | 8080, 3000, 9090, 9187, 5432 | Stop the conflicting process or the other compose stack |
| Seed takes many minutes | Competing load, or the database is mid-`ANALYZE` on a slow disk | Wait; do not start the run "just to save time" — a partially seeded dataset invalidates the numbers |
| A red run that "should" be green | Check `k6_business_refusals_total` first: a refusal is not an error, but a rising refusal rate means the manipulation placement is colliding and the mix is not what the run claims | Investigate the generator's slot-time grid |
| Manipulation p95 looks great, but `k6_business_refusals_total` ≈ the number of manipulations | Stale future: the dataset still holds the previous run's manipulated slots, so every create-slot is refused (harmless for the mix, useless as a measurement) | Re-run without `--no-reset`; a systemic refusal also trips the `checks` threshold |

## When to check that the load test has not leaked into the normal workflow

```bash
./gradlew build --no-daemon --console=plain    # must be exactly as fast and as green as before
grep -rn "loadtest" build.gradle.kts .github .githooks    # must return nothing
```

## Out of scope here

Optimising the service to hit the targets; performance claims for endpoints other than availability
and the booking manipulation; running the load test in CI; fixing the pre-existing metric-name
defects in the baseline dashboard (`minidoodle_meetings_created_total`,
`minidoodle_meetings_deleted_total`, `minidoodle_availability_query_total` — none of which are the
names the application publishes).
