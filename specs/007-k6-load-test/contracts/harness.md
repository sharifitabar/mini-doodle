# Contract: Harness, Compose Wiring and Dashboards

**Feature**: `007-k6-load-test` | **Date**: 2026-10-18

Everything a reader needs to know about *how* the harness is wired: the services, the scripts, the
environment surface, and the dashboards a run lands in.

## 1. Compose services

### Added to the base stack (always on)

| Service | Image | Purpose | Notes |
| --- | --- | --- | --- |
| `postgres-exporter` | `prometheuscommunity/postgres-exporter:v0.20.1` | database pressure | `DATA_SOURCE_NAME=postgresql://minidoodle:minidoodle@db:5432/minidoodle?sslmode=disable`, port `9187`, `depends_on: db (service_healthy)` |

It is in the base stack rather than behind the `loadtest` profile on purpose: a scrape job whose
target only exists during a load test would leave a permanently `DOWN` target in Prometheus for
every normal `docker compose up`. It is ~20 MB and its metrics are useful beyond the load test.

### Behind the `loadtest` profile (never started by `docker compose up`)

| Service | Image | Command | Purpose |
| --- | --- | --- | --- |
| `seed` | `postgres:16` | `psql --host=db --username=minidoodle --dbname=minidoodle --set=ON_ERROR_STOP=1 --set=user_count=${SEED_USERS:-100} --file=/sql/seed.sql` | provisioning. `docker compose --profile loadtest run --rm seed /sql/reset.sql` reuses it for reset |
| `k6` | `grafana/k6:2.3.0` | `k6 run -o experimental-prometheus-rw /scripts/booking.js` | the generator |

`docker compose up` thus behaves exactly as it does today (FR-028, and the constitution's
"local full-stack run works via `docker-compose up`").

## 2. Environment surface

| Variable | Default | Consumed by | Meaning |
| --- | --- | --- | --- |
| `K6_BASE_URL` | `http://app:8080` | compose → container `BASE_URL` | service under test. The container variable is `BASE_URL`. The host override is `K6_BASE_URL` |
| `TEST_ID` | `local` (set by `run.sh` to a UTC timestamp) | k6 | run identity. Becomes the `testid` wide tag |
| `WINDOW_FROM` / `WINDOW_TO` | empty (set by `run.sh`) | k6 | ISO-8601 read window resolved from the seeded data |
| `LOAD_VUS` | `100` | k6 | resident simulated users |
| `SEED_USERS` | `100` | seed service (compose interpolation) | how many users provisioning creates. **Must be ≥ `LOAD_VUS`** or a VU maps to a non-existent identity and produces `403`s |
| `LOAD_THINK_TIME_SECONDS` | `10` | k6 | think time between actions |
| `LOAD_BODY_SAMPLE_EVERY` | `10` | k6 | how often a read response is parsed and asserted |
| `LOAD_RAMP_SECONDS` | `30` (`10` under `--smoke`) | k6 | ramp-up stage duration |
| `LOAD_STEADY_SECONDS` | `300` (`30` under `--smoke`) | k6 | steady-state stage duration |
| `LOAD_RAMPDOWN_SECONDS` | `30` (`10` under `--smoke`) | k6 | ramp-down stage duration |
| `SMOKE` | `0` (`1` under `--smoke`) | k6 | omits the mix threshold. A short run cannot guarantee 4–6% |
| `MIX_TOLERANCE` | `0.01` | k6 | half-width of the mix band around 5% |
| `TARGET_AVAIL_P95_MS` | `300` | k6 | availability p95 bound (FR-026, overridable so the verdict itself can be tested) |
| `TARGET_AVAIL_P99_MS` | `1000` | k6 | availability p99 bound |
| `TARGET_MANIP_P95_MS` | `500` | k6 | manipulation-sequence p95 bound |
| `TARGET_ERROR_RATE` | `0.01` | k6 | error-rate bound (expected refusals excluded) |
| `K6_PROMETHEUS_RW_SERVER_URL` | `http://prometheus:9090/api/v1/write` | k6 | remote-write endpoint |
| `K6_PROMETHEUS_RW_TREND_STATS` | `p(90),p(95),p(99),avg,min,max` | k6 | which percentiles become series |
| `K6_PROMETHEUS_RW_PUSH_INTERVAL` | `5s` | k6 | how often samples leave the generator |

**Trap**: compose interpolates the whole file for *every* command, so these must use `:-` defaults.
Using `${TEST_ID:?}` would break a plain `docker compose up`. The compose file carries a comment
saying so.

## 3. Changed existing files

| File | Change | Why |
| --- | --- | --- |
| `prometheus/prometheus.yml` | add a `postgres` scrape job (`postgres-exporter:9187`) | database pressure |
| `docker-compose.yml` (prometheus) | add `--web.enable-remote-write-receiver` | required for k6's remote-write output |
| `grafana/provisioning/datasources/prometheus.yml` | pin `uid: PBFA97CFB590B2093` | the existing dashboard already hardcodes that UID. Pinning makes it real instead of auto-assigned |
| `.dockerignore` | add `loadtest` | keeps the harness out of the application image build context |
| `.gitignore` | ignore `loadtest/results/*` (keep `.gitkeep`) | run artifacts are not source |
| `.specify/memory/constitution.md` | amended **1.0.0 → 1.1.0**: `Load testing` and `DB-side metrics` rows added to the locked stack. One workflow bullet stating the load test is off the critical path | the locked table enumerates test/observability tooling, so the additions belong there (see plan.md Constitution Check) |
| `src/main/resources/application.yml` | `management.metrics.distribution.percentiles-histogram.http.server.requests: true` | service-side latency percentiles (research D8). Added after the running service was confirmed to publish counts and sums only, with no `http_server_requests_seconds_bucket` series |

## 4. Scripts

| Script | Usage | Does |
| --- | --- | --- |
| `loadtest/run.sh` | `./loadtest/run.sh [--no-reset] [--smoke] [--testid ID] [--no-build]` | `up -d` the stack → poll `http://localhost:8080/actuator/health` until `UP` (180 s timeout) → **reset + seed** (reset first, `--no-reset` skips both, for a deliberate continuation run) → resolve the read window from the database → run k6 with `TEST_ID`/`WINDOW_FROM`/`WINDOW_TO` → report the Grafana URL → **exit with k6's code** |
| `loadtest/reset.sh` | `./loadtest/reset.sh` | runs `reset.sql` only |

`--smoke` provisions a **reduced** dataset (`SEED_USERS=10`, `LOAD_VUS=10`, ramp 10 s, steady 30 s,
ramp-down 10 s) to check the wiring in about a minute instead of fifteen. Latency, error-rate and
`checks` thresholds still apply. The mix threshold is omitted (`SMOKE=1`), because a run that short
cannot guarantee a 4–6% mix. A successful smoke run proves the connections between components, **not** performance.

Readiness is enforced by the script, not by compose health: the runtime image
(`eclipse-temurin:21-jre`) has no `curl`/`wget`, and `depends_on` cannot express "after the dataset
is seeded" (research D12).

**Window resolution trap**: `psql` prints `timestamptz` as `2026-09-23 10:00:00+00`, which the API
rejects. The query must format explicitly:
`to_char(x AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS') || 'Z'`.

## 5. Dashboard contract

| Item | Value |
| --- | --- |
| File | `grafana/dashboards/mini-doodle-load-test.json` (picked up by the existing file provider from `/var/lib/grafana/dashboards`) |
| UID / title | `minidoodle-load-test` / `Mini Doodle - Load Test (1000 users)` |
| Datasource | `{"type":"prometheus","uid":"PBFA97CFB590B2093"}` — same as the baseline dashboard |
| Template variable | `testid` ← `label_values(k6_http_reqs_total, testid)`, default = most recent run |
| Refresh / range | `10s` / `now-30m` |
| Tags | `mini-doodle`, `load-test` |

Panels, grouped as a reader would use them:

1. **Run overview** — resident users (`k6_vus`), achieved request rate, error rate, measured mix
   (`k6_manipulation_share_rate`), refusals by step (`k6_business_refusals_total`).
2. **Client latency by flow** — `k6_http_req_duration_p95/_p99` split by `flow`, plus
   `k6_manipulation_sequence_duration_p95` against the 500 ms line.
3. **Throughput by flow** — `sum by (flow) (irate(k6_http_reqs_total{testid=~"$testid"}[$__rate_interval]))`.
4. **Service side** — `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{uri=~"/api/v1/.*"}[$__rate_interval])) by (le, uri))`,
   request rate by `uri`, and 5xx rate.
5. **Database pressure** — backends (`pg_stat_database_numbackends`), sessions by state
   (`pg_stat_activity_count`), locks (`pg_locks_count`), deadlocks and rollbacks
   (`pg_stat_database_deadlocks`, `pg_stat_database_xact_rollback`), and blocks read vs hit
   (`pg_stat_database_blks_read` / `_blks_hit`).
6. **Truthfulness** — `k6_checks_rate` and `k6_http_req_failed_rate`, so a green latency panel
   cannot be mistaken for a healthy run if the checks or the failure rate disagree.

Because both the generator's view and the service/database view are on the same dashboard against
the same clock, US4 AC4 ("was it the database or the service?") is answerable from the screen: the
service-side latency and the database's active sessions/locks either rise together or they don't.

## 6. Non-goals (kept out deliberately)

- Not referenced by Gradle, `check`, `.githooks/pre-push`, or `.github/workflows/ci.yml` — asserted
  in verification, not only intended.
- Not baked into the application image (`.dockerignore`).
- No production code, no schema migration, no new endpoint, no new Gradle dependency.
- The app's connection pool and Postgres settings are **not** tuned. Whatever the run finds is
  reported as a finding (spec assumption "measurement, not optimisation").
