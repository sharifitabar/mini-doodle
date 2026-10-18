# Implementation Plan: k6 Load Test for Booking-Scale Performance

**Branch**: `007-k6-load-test` | **Date**: 2026-10-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-k6-load-test/spec.md`

## Summary

Add a self-contained, reproducible load test that runs the existing service through the local
`docker compose` stack, drives 1000 concurrent simulated users against a pre-provisioned dataset of
~700,000 slots and ~50,000 meetings, and reports latency, throughput, error rate and database
pressure in Grafana — with a machine-detectable pass/fail verdict.

The technical approach, in one line: **a k6 script (grafana/k6:2.3.0) whose per-second measurements
reach the existing Prometheus via the remote-write receiver, a pure-SQL seeder run through `psql`
against the existing schema, a `postgres_exporter` sidecar for database pressure, one new
provisioned Grafana dashboard segmented by a `testid` tag, and a small `loadtest/run.sh` that
sequences readiness → seed → run and propagates k6's exit code (99 on a threshold breach).**

No production code changes, no schema changes, no new Gradle dependency, and nothing new on the
`./gradlew build` / CI / pre-push path.

## Technical Context

**Language/Version**: Java 21 (unchanged, only optional config). Load scenarios: JavaScript
(ECMAScript as implemented by k6 v2.3.0). Dataset: SQL (PostgreSQL 16). Orchestration: POSIX `sh`.

**Primary Dependencies**: `grafana/k6:2.3.0` (new, container-only);
`prometheuscommunity/postgres-exporter:v0.20.1` (new, container-only); Prometheus `v2.54.1`
(existing, plus a command-line flag and one scrape job); Grafana `10.4.7` (existing, plus one
dashboard); PostgreSQL 16 (existing). **No new JVM or Gradle dependencies.**

**Storage**: PostgreSQL 16, existing schema (`V1__init.sql`, `V2__meetings.sql`,
`V3__add_updated_at_to_users_and_calendars.sql`). **No new migration** — the feature adds rows,
not tables. Provisioned footprint: 1000 users + 1000 calendars + ~700k slots + ~50k meetings +
~100k participants ≈ 850k rows, roughly 0.4–0.6 GB with indexes.

**Testing**: No new JUnit tests — there is no production code to cover, and the coverage floors in
`build.gradle.kts` are unaffected because no `com.minidoodle` class changes. Verification is:
(a) post-seed SQL assertions (`loadtest/sql/verify.sql`), (b) a one-VU smoke run (`--once`-style
short profile), (c) a deliberate threshold-breach check proving k6 exits non-zero, (d) proof that
`./gradlew build` neither runs nor references the load test.

**Target Platform**: A local developer machine (macOS arm64 and Linux x86-64) with Docker Compose.
The load generator, the service, the database, and the dashboards all run as containers.

**Project Type**: Single Spring Boot service plus a local observability stack. This feature adds a
load-test harness around it — no new deployable, no new service endpoint.

**Performance Goals**: 1000 concurrent simulated users held for a 10-minute steady state; ~90–100
requests/second aggregate (1000 users, ~8–12 s think time); availability reads p95 < 300 ms and
p99 < 1 s; manipulation sequences p95 < 500 ms; error rate < 1%; request mix 5% ± 1 percentage
point of manipulations.

**Constraints**: Must run entirely locally with `docker compose`; the dataset must exist before the
measured window (never built by the run); ramp-up, seeding and teardown excluded from the measured
window; expected `409` refusals must not count as errors; the load test must never run as part of
`check`/`build`/CI; a target breach must be detectable by a script; the app's own configuration is
**not** tuned to hit the targets; two runs must be distinguishable in Grafana.

**Scale/Scope**: Per run: ~55,000–60,000 HTTP requests, ~2,800–3,000 manipulation sequences; 12.5
minutes of load plus 1–3 minutes of seeding. Known host limits: k6 keeps 1000 VU runtimes resident
(≈1–3 GB), Docker Desktop must have the memory to spare; the app's HikariCP pool is at Spring
Boot's default of 10 connections and Postgres `max_connections` at 100, which is expected to be the
first thing the run reveals.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Verdict | Evidence / required action |
| --- | --- | --- |
| I. Domain-Centric Hexagonal Architecture (NON-NEGOTIABLE) | **PASS** | No code under `com.minidoodle` is added or moved. k6 JS, SQL and shell live in a new top-level `loadtest/`; the single production-file touch is optional configuration in `application.yml` (see D8). No layering, no ports, no adapters touched. |
| II. Database as Source of Truth (Constraints First) | **PASS, with a recorded deviation** | No schema change, no Flyway migration, and the overlap/double-booking invariants stay enforced by `ex_slot_no_overlap` and `uq_meetings_slot` — the seeder is bound by the same constraints as the application. Deviation: the seeder writes with SQL rather than through the domain, justified in Complexity Tracking. `loadtest/sql/verify.sql` asserts the seeded data satisfies what the domain would have enforced. |
| III. Test Discipline with Real Dependencies | **PASS** | The load test runs against the real PostgreSQL and the real HTTP API — no mocks, no in-memory substitutes, consistent with the project's integration-first stance. It adds no test to the `test` task, so build time and the JaCoCo floors are untouched. |
| IV. Simplicity & YAGNI at Honest Scale | **PASS** | No new Gradle dependency, no new module, no new production abstraction. SQL seeding beats a Java runner precisely because the task is bulk data, not behavior. The harness stays a shell script plus a k6 script. |
| V. Observable & Documented Service | **PASS, with required work** | New metrics surface (k6 client-side, Postgres-side) plus a provisioned dashboard; `README.md` and `AGENTS.md` gain the run procedure, metric definitions, targets and the "never on the critical path" rule in the same change. |
| Technology Stack (Locked) | **PASS — amended** | The locked table enumerates testing and observability tooling, so the additions are recorded there rather than left implicit: the constitution was amended to **1.1.0** (MINOR) with a `Load testing` row (k6 via a `loadtest` compose profile → Prometheus/Grafana), a `DB-side metrics` row (postgres_exporter), and one *Development Workflow & Quality Gates* bullet stating the load test is deliberate, manual, and must never be wired into `check`, `build`, CI, or the pre-push hook. No existing row or principle changed. |
| Development Workflow & Quality Gates | **PASS** | Conventional Commits; `docker-compose up` keeps working unchanged (new services are additive, the load generator is behind a `loadtest` profile); CI still runs exactly `./gradlew build`. |

**Post-Phase-1 re-check**: unchanged. The Phase 1 design introduced no production dependency, no
schema change, and no new endpoint. The locked-stack item was resolved by amending the constitution
to **1.1.0**, leaving the two recorded deviations in Complexity Tracking (SQL seeding instead of the
application write path; harness verification by assertions and smoke runs instead of CI-executed unit
tests). Both were already true before design.

## Project Structure

### Documentation (this feature)

```text
specs/007-k6-load-test/
├── plan.md              # This file
├── spec.md              # Feature specification (approved)
├── research.md          # Phase 0 output — D1–D13 decisions with rationale
├── data-model.md        # Phase 1 output — dataset model, UUID namespace, invariants
├── quickstart.md        # Phase 1 output — run procedure, interpretation, troubleshooting
├── contracts/
│   ├── api-usage.md     # HTTP surface the generator depends on, incl. expected refusals
│   ├── load-scenario.md # Scenario shape, mix, tags, metrics, thresholds, exit code
│   └── harness.md       # compose services/profiles/env, scripts, dashboard & datasource contract
├── checklists/
│   └── requirements.md  # Spec quality checklist (from /speckit.specify)
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root)

```text
loadtest/                                  # NEW top-level harness (excluded from the app image)
├── run.sh                                 # readiness → reset/seed → window → run; propagates k6's exit code
├── reset.sh                               # removes the load-test dataset (ownership-scoped, cascades)
├── sql/
│   ├── seed.sql                           # deterministic 1000-user dataset; idempotent; ANALYZE at the end
│   ├── reset.sql                          # deletes the load namespace (cascades)
│   └── verify.sql                         # post-seed assertions + reports counts and timing
├── k6/
│   ├── booking.js                         # the scenario: mixed flows, thresholds, custom metrics
│   └── lib/
│       ├── identity.js                    # VU → user mapping; UUID construction; slot-time grid
│       └── report.js                      # handleSummary: JSON artifact + human-readable verdict
└── results/                               # run artifacts (git-ignored except .gitkeep)

grafana/
├── dashboards/
│   ├── mini-doodle-baseline.json          # EXISTING (untouched)
│   └── mini-doodle-load-test.json         # NEW dashboard, uid: minidoodle-load-test
└── provisioning/datasources/prometheus.yml  # EDITED: pin `uid` so dashboard references are stable

prometheus/
└── prometheus.yml                         # EDITED: add `postgres` scrape job

docker-compose.yml                         # EDITED: postgres-exporter (base) + seed/k6 (loadtest profile)
.dockerignore                              # EDITED: exclude loadtest/ from the app image context
.gitignore                                 # EDITED: ignore loadtest/results/*
README.md                                  # EDITED: "Load testing" section
AGENTS.md                                  # EDITED: rule that the load test is off the critical path
src/main/resources/application.yml         # EDITED (conditional, D8): enable http.server.requests histograms
```

**Structure Decision**: The harness is a new top-level `loadtest/` directory next to the existing
`prometheus/` and `grafana/` directories, so it is discoverable beside the stack it drives and is
trivially excluded from the application image (`.dockerignore`). It deliberately does **not** live
under `src/`: nothing in it may be reachable from `check`, `build`, the JaCoCo class directories, or
the Spring Boot jar. Container services stay in the single root `docker-compose.yml` because the
existing stack (db/app/prometheus/grafana) is already defined there and the load test must reuse it,
not fork it; the generator and seeder are hidden behind a `loadtest` compose profile so
`docker compose up` behaves exactly as before.

## Phase 0 — Research

`research.md` records thirteen decisions (D1–D13), each with the alternative that was rejected. The
ones that shaped the design:

- **D1 Transport**: k6 → Prometheus **remote write** (`-o experimental-prometheus-rw`), which needs
  `--web.enable-remote-write-receiver` on the existing Prometheus and
  `K6_PROMETHEUS_RW_TREND_STATS=p(90),p(95),p(99),avg,min,max` (the default is only `p(99)`), giving
  `k6_http_req_duration_p95` / `_p99` series that the dashboard can query directly.
- **D2 Database pressure**: a new `postgres_exporter` sidecar, because connection/lock/transaction
  pressure cannot be seen from the application's own meters.
- **D3 Dataset**: pure SQL seeding, because 800k rows through the HTTP write path would *be* the
  load and would leave the database at full size only at the end.
- **D4 Load shape**: `ramping-vus` to 1000 with think time, plus a per-VU deterministic mix rule,
  because 1000 *resident user sessions* is what the spec approved, and `constant-arrival-rate`
  would report demand that was never generated.
- **D5 Verdict**: k6 thresholds plus its built-in exit code 99, so a breach is script-detectable.
- **D6 Refusals**: `http.setResponseCallback(http.expectedStatuses(...409))` so concurrent-booking
  refusals are classified as expected and excluded from the error rate.
- **D7 Run identity**: a `testid` wide tag on every metric, so two runs in one Grafana session are
  distinguishable.
- **D8 Service-side histograms**: conditional one-line config change to publish
  `http_server_requests_seconds_bucket`, which the service-side latency requirement needs.
- **D13 Run isolation**: reset + reseed by default (a second run against a stale dataset would be
  refused on every write), with the `checks` threshold as the guard that makes such a run red
  instead of deceptively green.

## Phase 1 — Design

- `data-model.md` — the load-test data model: user/calendar/slot/meeting/participant rows, the
  deterministic UUID namespace, the slot time grid, per-user count formulas, invariants, and the
  reset semantics.
- `contracts/api-usage.md` — exactly which HTTP requests the generator issues, with the request
  bodies, headers, and the expected status set including the refusals that must not be errors.
- `contracts/load-scenario.md` — the scenario contract: executors, stages, mix rule, tags, custom
  metrics, thresholds, summary artifact and exit codes.
- `contracts/harness.md` — the compose contract (services, profiles, environment variables,
  ports), the script entry points, and the dashboard/datasource contract.
- `quickstart.md` — the run book: prerequisites, exact commands, what to look at, how to read the
  verdict, and the known traps.

**Agent context**: this repository does not ship `.specify/scripts/bash/update-agent-context.sh`
(the convention is hand-maintained `AGENTS.md`). Updating `AGENTS.md` and `README.md` is therefore a
deliverable task in `tasks.md`, not a scripted plan step.

## Risks and Open Questions

| # | Risk | Handling |
| --- | --- | --- |
| R1 | The run reports **failure** on the first attempt: at a 10-connection pool and 1000 users, pool queueing is a likely finding. | Expected and accepted by the spec's assumptions. The outcome is the deliverable; tuning is a separate change and is explicitly out of scope. |
| R2 | The host, not the service, is the bottleneck (k6 holds 1000 VU runtimes; Docker Desktop CPU/RAM). | Report k6's own `k6_vus`, `k6_iterations_total` and achieved request rate next to service metrics so a generator-bound run is visible; document Docker memory requirements in `quickstart.md`. |
| R3 | Closed-loop caveat: for `ramping-vus`, a slow service reduces the offered rate. | Stated in `contracts/load-scenario.md` and `quickstart.md`; latency thresholds are still meaningful, throughput claims are labelled as achieved-not-offered. |
| R4 | Grafana datasource UID is not pinned (`PBFA97CFB590B2093` in the existing dashboard is auto-assigned). | Pin the UID explicitly in the datasource provisioning file (see D9) so both dashboards resolve reliably on a fresh volume. |
| R5 | The existing baseline dashboard queries three metric names that do not exist (`minidoodle_meetings_created_total`, `minidoodle_meetings_deleted_total`, `minidoodle_availability_query_total`) and its p95 panel assumes histogram buckets exist. | Pre-existing defect from feature 004, **not** silently bundled: the new dashboard uses the correct names. Fixing the baseline is offered as a separate, optional change. |
| R6 | `http_server_requests_seconds_bucket` may not be published by default (D8). | Verified in T0xx; the fix is a one-line config addition, listed as a conditional task. |
| R7 | Repeated runs bloat the database (delete + re-insert of ~850k rows). | `reset.sh` deletes only the load dataset and re-runs `ANALYZE`; `quickstart.md` also documents a full `down -v` path for a clean slate. |
| R8 | A run without a reset silently measures a flow that performs no writes (every create-slot refused on a stale future). | Reset + reseed are the default; the scenario asserts `201` on both write steps, so a systemic refusal fails the `checks` threshold. Recorded in D13 and in `quickstart.md`'s troubleshooting table. |

## Complexity Tracking

| Deviation | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| The seeder writes rows with SQL, bypassing the domain (Constitution II's spirit, though not its letter — no constraint is bypassed). | The spec fixes the dataset as a precondition of the measured window; it must exist at full size before any load, and 850k rows must land in seconds. | Driving 800k rows through `POST /api/v1/slots` (a k6 setup stage or a Spring `ApplicationRunner`) makes dataset growth the dominant traffic, takes orders of magnitude longer, and violates FR-010 as clarified. |
| The harness is verified by SQL assertions, a smoke run and a deliberate-failure check rather than by the automated JUnit/Testcontainers discipline of Principle III. | There is no production code in this feature, so there is nothing for the coverage floors to measure; and the work gate now explicitly forbids putting a 15-minute load test on `check`/CI. | Writing JUnit tests for shell/SQL/k6 assets would either assert nothing meaningful or require a running stack inside the test task, which is exactly the coupling the gates exist to prevent. |

## Out of Scope (recorded, not silently omitted)

- Optimising the service, schema, indexes or connection pool to reach the targets.
- Any performance claim for flows other than availability checks and the
  add-slot-then-book manipulation.
- CI execution or scheduling of the load test; container/image scanning; dependency audit.
- Load-testing the observability stack itself (Prometheus/Grafana capacity).
- Fixing the pre-existing baseline-dashboard metric-name defects — **deferred by decision** to a
  separate change after this feature (see R5), so that feature 007's diff stays about the load test.

## Next Step

`/speckit.tasks` to generate `tasks.md` from this plan, `data-model.md`, `contracts/` and
`quickstart.md`.
