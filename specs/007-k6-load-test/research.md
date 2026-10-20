# Phase 0 Research: k6 Load Test for Booking-Scale Performance

**Feature**: `007-k6-load-test` | **Date**: 2026-10-18

Every decision below was taken against the *actual* repository (verified file contents, not
assumptions) and against current upstream documentation. Each entry states the decision, why, the
alternative rejected, and the evidence.

---

## D1 — How k6 measurements reach Grafana: Prometheus remote write

**Decision**: run k6 with `-o experimental-prometheus-rw`, `K6_PROMETHEUS_RW_SERVER_URL=http://prometheus:9090/api/v1/write`,
`K6_PROMETHEUS_RW_TREND_STATS=p(90),p(95),p(99),avg,min,max`, `K6_PROMETHEUS_RW_PUSH_INTERVAL=5s`,
and add `--web.enable-remote-write-receiver` to the existing Prometheus command.

**Rationale**: the requirement is that a run's results appear in Grafana *while it runs* and remain
afterwards. Remote write streams samples during the run and Prometheus persists them, so the
generator stopping does not remove the data (FR-022, FR-024, SC-002). Adding the receiver flag is a
one-line change to a service that already exists.

**Verified series names** (from the official k6 Prometheus dashboard, queried by `testid`):
`k6_vus`, `k6_http_reqs_total`, `k6_http_req_failed_rate`, `k6_checks_rate`,
`k6_iterations_total`, `k6_iteration_duration_*`, `k6_dropped_iterations_total`, and with trend
stats `k6_http_req_duration_p95` / `_p99` / `_min` / `_max` / `_avg`.

**Alternatives rejected**:
- *Native histograms* (`K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM=true`) — needs Prometheus ≥2.40
  with `--enable-feature=native-histograms`; a second experimental flag on the critical path for
  Grafana visibility, and grafana.com's native-histogram dashboard expects histogram buckets that
  the counter/gauge path does not produce.
- *`--out json=...` plus a sidecar* — an extra moving part to write and operate.
- *Grafana Cloud / `k6 cloud`* — the spec requires everything to run locally.
- *Scraping a k6 Prometheus endpoint* — k6 has no scrape endpoint; remote write is the supported
  direction.

**Evidence**: k6 docs, "Prometheus remote write" (metrics mapping table, options table, the
`-web.enable-remote-write-receiver` requirement, and the `testid` wide-tag guidance); dashboard
19665 panel expressions.

---

## D2 — Database pressure: `postgres_exporter` sidecar

**Decision**: add `prometheuscommunity/postgres-exporter:v0.20.1` to `docker-compose.yml` (base
profile, always running), configured with
`DATA_SOURCE_NAME=postgresql://minidoodle:minidoodle@db:5432/minidoodle?sslmode=disable`, exposed on
`9187`, and add a `postgres` scrape job to `prometheus/prometheus.yml`.

**Rationale**: FR-023 and US4 need connections in use, active transactions, lock waits and
deadlocks. The application's Micrometer meters describe what the application did, not what the
database was doing to serve it, so "the database was the bottleneck" (US4 AC4) would be
unprovable without a database-side exporter. Deadlock and rollback counters matter specifically
here: `ex_slot_no_overlap` produces serialization/deadlock SQLSTATEs under concurrent writes, and a
contended write surfaces as `409` (overlap) or `503` with `Retry-After` (deadlock), because the
persistence adapter translates those SQLSTATEs instead of retrying the write (see
`docs/adr/0001-slot-insert-contention.md`).

**Alternatives rejected**:
- *`pg_stat_statements`* — needs `shared_preload_libraries` plus an extension in the database
  image; a much larger change for marginal gain over the exporter's default collectors.
- *Application-side custom meters* — requires production code (Constitution IV) and still cannot
  see locks or backend counts.
- *Prometheus PostgreSQL adapter* — same thing with an extra hop.

**Consequence to accept**: the exporter belongs to the base stack, not the `loadtest` profile,
otherwise the default `docker compose up` would show a permanently `DOWN` Prometheus target. It is
tiny (≈20 MB) and always-on database metrics are useful to the existing observability story too.

**Panels it enables**: `pg_stat_activity_count` by state
(idle/active/idle-in-transaction), `pg_stat_database_numbackends`, `pg_locks_count` by mode,
`pg_stat_database_xact_commit` / `pg_stat_database_xact_rollback`,
`pg_stat_database_deadlocks`, `pg_stat_database_blks_hit` vs `pg_stat_database_blks_read`,
`pg_up` and `pg_exporter_last_scrape_error`.

---

## D3 — Dataset provisioning: pure SQL through `psql`

**Decision**: seed with `loadtest/sql/seed.sql` executed by a short-lived `postgres:16` container
(`docker compose --profile loadtest run --rm seed`), using `INSERT ... SELECT` over
`generate_series`, deterministic UUIDs, and a closing `ANALYZE`.

**Rationale**: FR-010 (as clarified) requires the full dataset to exist before the measured window;
~700k slots + ~100k meeting/participant rows must land in seconds-to-tens-of-seconds. One statement
per table achieves that. `ANALYZE` is non-negotiable: without fresh statistics the planner may
choose a poor plan and the latency numbers would describe a planning accident rather than the
service (see data-model.md).

**Alternatives rejected**:
- *k6 `setup()` creating data through the API* — becomes the dominant traffic, takes minutes to
  hours, and contradicts FR-010.
- *A Spring Boot `ApplicationRunner`* (extending `DevDataSeeder`) — jOOQ inserts of 850k rows are
  orders of magnitude slower, it would need production code (Constitution IV) and it would run
  inside the app under test.
- *COPY from a generated file* — faster in principle but adds a file-generation step and a
  hundreds-of-megabytes artifact for no measurable benefit at this size.
- *Docker-entrypoint init scripts under `/docker-entrypoint-initdb.d`* — only run on an empty data
  volume, which breaks idempotency and re-runs.

---

## D4 — Load shape: 1000 resident VUs with think time (`ramping-vus`)

**Decision**: one scenario, `ramping-vus`, stages `2m → 1000`, `10m → 1000`, `30s → 0`, each VU
pinned to one dataset user, with an 8–12 s think time between actions.

**Rationale**: the approved clarification is "1000 users concurrent at peak". `ramping-vus` is the
only executor where 1000 *user sessions* are genuinely resident for the whole steady state, each
acting as itself (FR-017). With an ~10 s think time that yields ~90–100 requests/second, which is
what 1000 humans checking their calendar actually generates.

**Alternatives rejected**:
- *`constant-arrival-rate` (95/s reads + 5/s manipulations)* — gives an exact mix and no coordinated
  omission, but the VUs that are actually used are only those needed to sustain the rate (order of
  tens), so "1000 users active concurrently" would be false, and `preAllocatedVUs` only pre-warms
  them. Rejected because it contradicts the approved clarification.
- *1000 VUs with no think time* — the offered rate becomes unbounded (as fast as the service
  answers), turning a capacity check into a saturation stampede and making run-to-run comparison
  meaningless.
- *Closed-model ramping with `maxDuration` only* — no held steady state, so there is nothing to
  measure (FR-018).

**Accepted limitation (documented, not hidden)**: this is a closed-loop model. If the service slows
down, the offered rate drops with it, so the run measures *achieved* throughput. Latency thresholds
remain valid and meaningful; throughput is reported as achieved.

---

## D5 — Verdict mechanics: k6 thresholds and exit code 99

**Decision**: express the spec's targets as k6 `thresholds`; rely on k6's default non-zero exit
(99) when a threshold is breached; have `loadtest/run.sh` propagate that exit code; write both a
JSON artifact and a human summary from `handleSummary`.

**Rationale**: FR-026/SC-005 require the verdict to be detectable by an automated caller, not just
readable on a chart. This is the mechanism k6 provides natively, so no wrapper logic decides
pass/fail.

**Alternatives rejected**:
- *Report-only runs* — explicitly declined during clarification ("breach fails the run").
- *Parsing the summary JSON in a shell script and exiting* — reimplements what k6 already does,
  and loses the summary's inline pass/fail per threshold.

---

## D6 — Distinguishing expected refusals from failures

**Decision**: call `http.setResponseCallback(http.expectedStatuses(200, 201, 204, 409, 503))` (plus
the default 2xx range) so `http_req_failed` does not count business refusals or contention;
additionally record a `business_refusals` counter and a `slot_contention` counter, each tagged by
`step`; threshold the error rate as `http_req_failed: ['rate<0.01']` and *report* both rates
without thresholding them.

**Rationale**: FR-020 and the corresponding edge case. At 1000 users with a 5% manipulation rate,
losing a race for a slot is normal behavior, not a fault; counting it as an error would make every
run red and hide real 5xx failures. `409` covers a slot overlap or an already-booked slot. `503` was
added later: the service no longer retries a write that the database aborted as a deadlock victim,
it answers `Retry-After` instead (`docs/adr/0001-slot-insert-contention.md`), so a contended write
is an expected outcome of the harness's own load rather than a fault of the service.

**Alternatives rejected**: a `check()`-only approach (checks are pass/fail assertions, not an error
rate), and post-processing response codes in the summary (too late to be visible in Grafana).

**Risk noted**: the exact spelling of the response-callback API is verified in T0xx before the
scenario is finalized; a script-level fallback is to tag each request explicitly and derive the
error rate from a custom `Rate` metric.

---

## D7 — Run identity: the `testid` wide tag

**Decision**: generate `TEST_ID` in `run.sh` (UTC timestamp, overridable by `-e TEST_ID=...`), expose
it to the script via `__ENV.TEST_ID`, and set it as a scenario-wide tag so every k6 series carries
`testid`. The dashboard has a `testid` template variable sourced from
`label_values(k6_http_reqs_total, testid)`.

**Rationale**: FR-024/SC-006 — several runs in one Grafana session must be distinguishable, and the
service-side panels must be readable against the same wall-clock window.

**Alternatives rejected**: `K6_PROMETHEUS_RW_LABELS` (static per process, and script tags take
precedence over static labels anyway — so it adds nothing here), and comparing runs by time range
only (fragile when runs overlap or a run is aborted).

---

## D8 — Service-side latency percentiles: enable `http.server.requests` histograms (conditional)

**Decision**: verify whether `http_server_requests_seconds_bucket` is published by the running
service; if it is not, add
`management.metrics.distribution.percentiles-histogram.http.server.requests: true` to
`src/main/resources/application.yml`.

**Rationale**: FR-022 asks for service-side latency per flow, and the only honest service-side
source is `http.server.requests` with its `uri` tag. Micrometer's default `distributionStatisticConfig`
does **not** disable histograms explicitly, but Spring Boot's documented way to get percentile
histograms for HTTP requests is this property, which strongly implies buckets are not published by
default; the repository has no `management.metrics.distribution` configuration at all.

**Note**: this is the only possible production-file change in the whole feature, it is
configuration-only, and it also repairs the existing baseline dashboard's p95 panel (see R5). If the
buckets turn out to be present, this task is dropped and nothing changes.

**Alternatives rejected**: deriving service-side percentiles from k6 only (that measures the
client's view, not the service's), and logging/exporting timings by hand (production code, and it
duplicates `http.server.requests`).

---

## D9 — Grafana datasource UID must be pinned

**Decision**: add an explicit `uid` to `grafana/provisioning/datasources/prometheus.yml`, using the
exact value the existing dashboard already hardcodes (`PBFA97CFB590B2093`), and reference the same
UID from the new load-test dashboard.

**Rationale**: the datasource is provisioned **without** a UID today, so Grafana assigns one at
first provision, while `grafana/dashboards/mini-doodle-baseline.json` hardcodes
`"uid": "PBFA97CFB590B2093"` in 17 places. Pinning the UID to the value the dashboard already
expects makes both dashboards deterministic on a fresh volume and changes **nothing** that currently
works. Ordering matters here: had we instead invented a new UID, the existing dashboard would have
had to be edited (17 replacements) — more churn, more risk.

**Alternatives rejected**: inventing a readable UID and rewriting the existing dashboard (more
edits to a shipped artifact for no behavior gain), and referencing the datasource by name only
(Grafana's provisioning model keys on UID; name-only references are not portable across versions).

---

## D10 — Seeding slot times: past, on a 45-minute grid, with a fixed read window

**Decision**: per user, slot *i* starts at
`T0 − (i + 1) × 45 minutes + (userIndex mod 15) minutes`, duration 30 minutes, where `T0` is the hour
at seed time. Availability reads use the window `[T0 − 26 days, T0]`.

**Rationale**:
- *Past-dated* slots keep the future empty, which is exactly what the manipulation flow needs
  (`@Future` on `startAt`, and no overlap with existing slots for that calendar).
- A 45-minute grid with 30-minute slots leaves 15-minute holes, so the availability response is a
  rich alternating FREE/BUSY interval list rather than one merged block — reads return ~1,200–1,600
  intervals for the busiest users instead of a trivially small payload.
- 800 slots × 45 min = 25 days, inside the 31-day `MAX_SPAN` cap enforced by `TimeFrame`, so a
  single query can cover a user's entire history.
- The `userIndex mod 15` minute shift decorrelates users in the GiST index without ever creating an
  intra-calendar overlap (the shift is smaller than the 15-minute gap).

**Alternatives rejected**: contiguous hourly slots (they merge into one interval and shrink the
response), spreading slots over more than 31 days (the whole history could not be queried in one
call), and future-dated slots (they would collide with the manipulation flow and force the generator
to probe for gaps).

---

## D11 — Reset semantics: delete the load namespace, never truncate

**Decision**: every seeded entity gets a deterministic UUID in a reserved namespace
(`10000000-…` users, `20000000-…` calendars, `30000000-…` slots, `40000000-…` meetings).
`reset.sql` (a) deletes `meetings` whose slot belongs to a load-user calendar, then (b) deletes
`users` in the range `[10000000-0000-4000-8000-000000000000, 20000000-0000-4000-8000-000000000000)`
and lets `ON DELETE CASCADE` clear calendars and slots.

**Rationale**: FR-021/SC-008 need repeatable runs and comparable shapes without manual surgery, and
the demo user (`11111111-…`) that `DevDataSeeder` creates for local development must survive.
Deleting by a UUID range is index-friendly on the primary key; a leading-wildcard text match would
not be.

**Two traps this decision exists to avoid**:
1. `meetings.slot_id` is `ON DELETE RESTRICT`, so meetings must go first. But the meeting-ID
   namespace is **not** a valid predicate: meetings created by the manipulation flow during a
   previous run are written by the application with random UUIDs. The delete must be scoped by
   *calendar ownership* (`meetings.slot_id → time_slots.calendar_id → calendars.user_id`), or the
   subsequent `users` cascade aborts on a foreign-key violation. This is the same failure class
   `JooqSlotRepository` documents.
2. The manipulated slots of previous runs also live in the load users' calendars, so the same
   cascade removes them — which is exactly why `run.sh` resets and reseeds by default (D13).

**Alternatives rejected**: `TRUNCATE ... CASCADE` (wipes the demo user and any hand-made data, and
makes the reset destructive beyond the load test's own footprint), `docker compose down -v` as the
only reset (correct but slow — it throws away Prometheus and Grafana volumes too, which the spec
wants retained so a previous run stays queryable).

**Companion**: a full `docker compose down -v` path remains documented in `quickstart.md` for a
clean slate when the database has accumulated several runs of dead tuples.

---

## D12 — Readiness: sequenced by the orchestration script, not by compose health

**Decision**: `loadtest/run.sh` waits for `http://localhost:8080/actuator/health` to report `UP`
(host-side `curl`, with timeout and retry), then runs the seeder, then resolves the read window from
the database, then starts the k6 container.

**Rationale**: FR-002 requires that load never starts before the service is ready *and* the dataset
exists. The compose file currently has a healthcheck only for `db`; the application runtime image is
`eclipse-temurin:21-jre`, which contains neither `curl` nor `wget`, so adding an app healthcheck
would mean changing the delivered Dockerfile for the sake of the test harness. Sequencing in the
script also lets the seed step run strictly between readiness and load, which `depends_on` cannot
express.

**Alternatives rejected**: adding `curl` to the runtime image (production image churn for a test
concern), a Java-based in-container healthcheck (no `jshell` in a JRE), and relying on
`depends_on: service_healthy` alone (it cannot express "after the dataset is seeded", and the app
has no healthcheck to key on).

**Note**: because the harness's environment variables are interpolated for every compose command,
the load-test service must use `:-` defaults rather than `:?` required syntax — otherwise even
`docker compose up` would fail on an unset `TEST_ID`. This is a small trap worth a comment in the
compose file.

---

## D13 — Run isolation: reset + reseed by default, with a checks-based staleness guard

**Decision**: `loadtest/run.sh` performs **reset → seed** before every measured run (`--no-reset`
opts out), and the scenario asserts that each write actually returned `201`, so a run whose writes
are all *refused* fails the `checks` threshold instead of silently measuring nothing.

**Rationale**: the manipulation flow places its new slots on a deterministic
`(VU, iteration)` hour grid in the otherwise-empty future. On a *fresh* dataset that grid is
guaranteed collision-free. Without a reset, a second run re-attempts the same hours, every
create-slot returns `409`, and the run would report a beautiful manipulation-sequence p95 for a flow
that performed **zero** writes. Because expected refusals are deliberately excluded from the error
rate (D6), nothing would look wrong.

Two mechanisms, because one of them is a safety net and the other is the policy:

| Mechanism | Role | Why it is not sufficient alone |
| --- | --- | --- |
| Reset + reseed by default | Guarantees a clean, comparable dataset (FR-021, SC-008) | Someone will eventually pass `--no-reset` |
| `check()` that create-slot and book-meeting returned `201`, under the `checks > 0.99` threshold | Turns a stale-future run red | A small legitimate refusal rate is expected, so it catches only systemic staleness |

**Alternatives rejected**: making the slot grid depend on a per-run nonce (extra state passed from
script to generator, and it makes the dataset accumulate instead of being cleaned); treating every
`409` as an error (rejected in D6); a default `docker compose down -v` reset (destroys the previous
run's Prometheus/Grafana history, which the spec wants retained).
