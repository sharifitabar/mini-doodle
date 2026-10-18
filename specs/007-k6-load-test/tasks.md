# Tasks: k6 Load Test for Booking-Scale Performance

**Input**: Design documents from `/specs/007-k6-load-test/`

**Prerequisites**: `plan.md`, `spec.md` (user stories US1–US5), `research.md` (D1–D13), `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: **No JUnit tests are generated for this feature.** There is no production code to cover, and the constitution now forbids putting the load test on the `check`/CI path. Each user story instead carries an explicit **Verification** task that is the story's independent test (a manual/integration check, run with the harness itself). The rationale is recorded in `plan.md` → Complexity Tracking.

## Constraints that apply to every task

- **Do not commit or push without explicit per-change approval.** Conventional Commits, one logical change per commit.
- **Nothing from `loadtest/` may be referenced by** `build.gradle.kts`, `.githooks/pre-push`, or `.github/workflows/ci.yml`. `./gradlew build` must stay green and unchanged in duration.
- **No new Gradle dependency. No production code change** — the single, conditional exception is T036 (one configuration line in `application.yml`, research D8).
- **No schema change and no new Flyway migration.** The dataset is rows in the existing tables.
- Container image versions are pinned exactly as specified (`grafana/k6:2.3.0`, `prometheuscommunity/postgres-exporter:v0.20.1`).
- Bash/POSIX `sh` for harness scripts; no Bash-isms that break on macOS `sh`.

### Already complete (plan phase, uncommitted)

- `.specify/memory/constitution.md` amended **1.0.0 → 1.1.0** (Load testing + DB-side metrics rows; the off-critical-path workflow rule). No task needed; commit with the plan/tasks artifacts.
- `specs/007-k6-load-test/{spec,plan,research,data-model,quickstart}.md` and `contracts/*` written.

### Explicitly out of scope

- Tuning the service, schema, indexes or connection pool to hit the targets (spec assumption).
- Performance claims for any flow other than availability checks and the booking manipulation.
- Running the load test in CI, or on any schedule.
- Repairing the pre-existing metric-name defects in `grafana/dashboards/mini-doodle-baseline.json` (deferred by decision to a separate change; see plan R5).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: The harness needs a home that is invisible to Gradle, to CI, and to the application image.

- [x] T001 Create the harness directory tree with placeholders: `loadtest/sql/`, `loadtest/k6/lib/`, `loadtest/results/.gitkeep`
- [x] T002 [P] Exclude the harness from the application image build context by adding `loadtest` to `.dockerignore`
- [x] T003 [P] Ignore run artifacts by adding `loadtest/results/*` (except `.gitkeep`) to `.gitignore`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Stack wiring that every user story depends on: a Prometheus that can accept k6's samples, a database-side exporter, a stable Grafana datasource UID, the `loadtest` compose profile, and the orchestration entry point.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T004 [P] Add the `postgres-exporter` service to `docker-compose.yml` (base stack — no profile): image `prometheuscommunity/postgres-exporter:v0.20.1`, `DATA_SOURCE_NAME=postgresql://minidoodle:minidoodle@db:5432/minidoodle?sslmode=disable`, port `9187:9187`, `depends_on: db (condition: service_healthy)`
- [x] T005 [P] Add the `postgres` scrape job to `prometheus/prometheus.yml` targeting `postgres-exporter:9187`
- [x] T006 [P] Pin the datasource UID in `grafana/provisioning/datasources/prometheus.yml` to `uid: PBFA97CFB590B2093` (the value `mini-doodle-baseline.json` already hardcodes in 17 places), with a comment explaining that both dashboards depend on it
- [x] T007 Enable k6's remote-write receiver by adding `--web.enable-remote-write-receiver` to the `prometheus` command in `docker-compose.yml` (depends on T004 — same file)
- [x] T008 Add the `seed` and `k6` services behind the `loadtest` compose profile in `docker-compose.yml`: `postgres:16` with `./loadtest/sql` mounted and a `psql -v ON_ERROR_STOP=1 -v user_count=${SEED_USERS:-1000} -f` entrypoint; `grafana/k6:2.3.0` with `./loadtest/k6` and `./loadtest/results` mounted and the environment surface from `contracts/harness.md` (depends on T007 — same file). **Every variable must use `:-` defaults, never `:?`**, or a plain `docker compose up` breaks
- [x] T009 Create `loadtest/run.sh` with the orchestration skeleton: argument parsing (`--no-reset`, `--no-build`, `--smoke`, `--testid ID`, `--help`), host-side readiness polling of `http://localhost:8080/actuator/health` until `UP` with a 180 s timeout, step sequencing, `TEST_ID` generation (UTC timestamp), the Grafana URL printout, and k6's exit code propagated unchanged (depends on T008)
- [x] T010 Verify the foundation before any story work: `docker compose config` is clean; `docker compose up -d --build` brings the stack up with **both** Prometheus targets `UP`; `/api/v1/write` answers (405/400 rather than connection-refused); `docker compose up` with no profile does **not** start `seed` or `k6`

**Checkpoint**: The stack is wired and observable. User stories can now begin — in the order given below (see Dependencies).

---

## Phase 3: User Story 3 - A dataset at the stated scale (Priority: P1)

**Goal**: 1000 users, each with its own calendar, a slot history of 600–800 non-overlapping slots (busiest users at the 800 ceiling), and up to 100 of those slots already booked as meetings — provisioned before any load, idempotently, with a report.

**Independent Test**: From an empty database, run the provisioning step; count users, per-user slots and per-user meetings in the database and confirm they match the target shape; run `verify.sql` and confirm all invariants hold; run provisioning again and confirm the dataset is unchanged.

**Why this comes first**: the other stories measure against this data. Latency against an empty table would be meaningless (spec US3).

### Implementation

- [x] T011 [US3] Write `loadtest/sql/seed.sql`: the `users` and `calendars` inserts using the parameterized `generate_series(1, :user_count)` and the deterministic UUID formulas from `data-model.md` §2 (`10000000-…` users, `20000000-…` calendars), per `data-model.md` §1
- [x] T012 [US3] Add the `time_slots` insert to `loadtest/sql/seed.sql`: `slots(n) = 600 + (n × 37) mod 201` rows per user, `start_at = T0 − (i + 1) × 45 min + (n mod 15) min`, `duration_minutes = 30`, `T0 = date_trunc('hour', now())` captured once, all past-dated, `status = FREE`
- [x] T013 [US3] Add the `meetings` and `meeting_participants` inserts to `loadtest/sql/seed.sql`: `meetings(n) = (n × 13) mod 101` per user on that user's lowest-indexed slots, those slots flipped to `status = BUSY`, 2 participants per meeting (name + email, within the column length CHECKs)
- [x] T014 [US3] Make `loadtest/sql/seed.sql` idempotent: `ON CONFLICT DO NOTHING` throughout, single transaction, closing `ANALYZE users, calendars, time_slots, meetings, meeting_participants`
- [x] T015 [US3] Add the provisioning report to `loadtest/sql/seed.sql`: users, slots and meetings created plus elapsed wall-clock (FR-011/FR-012), and a `--smoke` reduced `:user_count` path (e.g. 10 users) so the dataset can be provisioned in about a second
- [x] T016 [P] [US3] Write `loadtest/sql/verify.sql` asserting invariants I1–I9 from `data-model.md` §4, each as a statement that fails loudly (e.g. `DO $$ BEGIN IF (…) THEN RAISE EXCEPTION …; END IF; END $$;`) and ending with a readable shape report
- [x] T017 [P] [US3] Write `loadtest/sql/reset.sql`: (a) delete `meetings` whose `slot_id` belongs to a load-user calendar — **ownership-scoped, never by meeting-ID namespace**, because manipulation-created meetings carry app-generated UUIDs; (b) delete `users` in the `10000000-…` range and let `ON DELETE CASCADE` clear calendars and slots; (c) `ANALYZE`
- [x] T018 [US3] Wire provisioning into `loadtest/run.sh`: reset (unless `--no-reset`) → seed → `verify.sql`, with `SEED_USERS` exported for the compose entrypoint and `--smoke` setting a small `SEED_USERS`/`LOAD_VUS` pair; fail fast if `LOAD_VUS > SEED_USERS`

### Verification

- [x] T019 [US3] Verify US3 independently: from an empty database, provision and confirm 1000 users each with exactly one calendar, no user above 800 slots or 100 meetings, the busiest users at the ceilings, `verify.sql` green; provision again and confirm identical counts; run `reset.sql` and confirm the dataset is gone while the `DevDataSeeder` demo user survives

**Checkpoint**: US3 is complete and independently verifiable with no k6 and no dashboards.

---

## Phase 4: User Story 1 - Run the load test locally and watch it live in Grafana (Priority: P1) 🎯 MVP

**Goal**: One documented procedure brings the stack up, provisions the dataset, drives 1000 concurrent users against the availability flow, shows traffic and latency in Grafana while the run is in progress, and prints a summary when it ends.

**Independent Test**: `./loadtest/run.sh --smoke` from a clean checkout: the stack reaches ready, load starts only after the dataset exists, Grafana populates during the run without any manual dashboard editing, the data is still queryable afterwards, and a summary is printed.

**Note**: this phase deliberately implements the **availability flow only**. The manipulation flow, the 5/95 mix and the verdict are US2 and US4.

### Implementation

- [x] T020 [P] [US1] Write `loadtest/k6/lib/identity.js`: `__VU` → user UUID mapping (`10000000-0000-4000-8000-` + `lpad(to_hex(n), 12, '0')`), `LOAD_VUS`/`LOAD_THINK_TIME_SECONDS`/`LOAD_BODY_SAMPLE_EVERY` reading with defaults, and the `WINDOW_FROM`/`WINDOW_TO` injection with a clear failure if unset
- [x] T021 [P] [US1] Write `loadtest/k6/lib/report.js`: `handleSummary` writing `/results/summary-${TEST_ID}.json` plus a stdout summary of request counts, achieved request rate and latency percentiles (the verdict line is completed in T035)
- [x] T022 [US1] Write `loadtest/k6/booking.js`: `options` with the `booking` scenario (`ramping-vus`, `2m → LOAD_VUS`, `10m → LOAD_VUS`, `30s → 0`, `gracefulRampDown: 30s`), the `testid` wide tag from `__ENV.TEST_ID`, and an `--smoke`-friendly duration override
- [x] T023 [US1] Implement the availability flow in `loadtest/k6/booking.js`: `GET /api/v1/availability` with the VU's own `X-User-Id` and the injected window, tagged `flow: availability`, `responseType: 'none'` except on every `LOAD_BODY_SAMPLE_EVERY`-th read, and an 8–12 s jittered think time per `contracts/load-scenario.md` §1
- [x] T024 [US1] Add the availability assertions to `loadtest/k6/booking.js`: status `200`, and on sampled reads a parsed body with non-empty `intervals` containing at least one `BUSY` interval (FR-016)
- [x] T025 [US1] Create `grafana/dashboards/mini-doodle-load-test.json`: uid `minidoodle-load-test`, title `Mini Doodle - Load Test (1000 users)`, the `testid` template variable over `label_values(k6_http_reqs_total, testid)`, `10s` refresh, and the run-overview + client-latency-by-flow + throughput panels (`k6_vus`, `k6_http_reqs_total`, `k6_http_req_duration_p95/_p99`, `k6_manipulation_share_rate`, `k6_business_refusals_total`) — `contracts/harness.md` §5
- [x] T026 [US1] Finish `loadtest/run.sh` for the happy path: pass `TEST_ID`/`WINDOW_FROM`/`WINDOW_TO`/`LOAD_VUS` to the k6 service, print the exact Grafana URL including `var-testid`, and preserve k6's exit code

### Verification

- [ ] T027 [US1] Verify US1 independently with `./loadtest/run.sh --smoke`: readiness is enforced before load, panels populate within the first minute while the run is in progress, data is still queryable after it ends and distinguishable by `testid`, and the summary artifact plus stdout summary are produced

**Checkpoint**: A complete, watchable measurement loop exists. This is the MVP.

---

## Phase 5: User Story 2 - A workload that mirrors real usage (95% checks, 5% manipulations) (Priority: P1)

**Goal**: 5% (±1 pt) of simulated requests are a manipulation — add a slot, then convert that same slot into a meeting — executed as one logical unit, with expected refusals reported separately from failures; 95% are availability checks.

**Independent Test**: Run the scenario for a short duration and inspect the recorded mix by flow: manipulations between 4% and 6%, everything else availability checks; refusals near zero and never counted as errors.

### Implementation

- [x] T028 [US2] Implement the manipulation flow in `loadtest/k6/booking.js`: `POST /api/v1/slots` on the per-`(VU, ITER)` exclusive future hour grid (`startAt` strictly future, `durationMinutes` 30, so one user's manipulations can never overlap each other or the past-dated seeded history), then `POST /api/v1/slots/{id}/meeting` on that same slot, both tagged `flow: manipulation`
- [x] T029 [US2] Add refusal classification to `loadtest/k6/booking.js`: `http.setResponseCallback(http.expectedStatuses({min:200,max:204}, 409))` so `409` is an expected status rather than an `http_req_failed`, plus a `business_refusals` Counter tagged by `step` (`create_slot` / `book_meeting`) — `contracts/api-usage.md` status table
- [x] T030 [US2] Add the mix to `loadtest/k6/booking.js`: the deterministic rule `(__ITER % 20) === (__VU % 20)` selecting the manipulation, the `manipulation_share` Rate metric (1 per manipulation iteration, 0 per read), and the `manipulation_sequence_duration` Trend wrapping both write calls as one unit
- [x] T031 [US2] Add the write-step checks and the mix threshold to `loadtest/k6/booking.js`: `201` assertions on both steps under the `checks > 0.99` threshold (the guard that makes a stale-future run red — research D13), and `manipulation_share` between `0.04` and `0.06`
- [x] T032 [US2] Add the mix-over-time and refusals-by-step panels to `grafana/dashboards/mini-doodle-load-test.json`

### Verification

- [ ] T033 [US2] Verify US2 independently with `./loadtest/run.sh --smoke`: the measured mix lands in 4–6%, every manipulation performs both steps against one slot, refusals are counted separately and never as errors, and the `flow` split is visible in Grafana

**Checkpoint**: The workload matches the stated production shape, and the mix is machine-checked.

---

## Phase 6: User Story 4 - A defensible performance verdict, with database pressure visible (Priority: P1)

**Goal**: An explicit pass/fail against the agreed targets that an automated caller can detect, plus database-level pressure on the same timeline as the traffic, enough to tell "the database was the bottleneck" from "the service was the bottleneck".

**Independent Test**: Run against a deliberately tight target set and confirm the run reports failure and exits non-zero; repeat with loose targets and confirm success; confirm database pressure is visible in the dashboard for the same window.

### Implementation

- [x] T034 [US4] Add env-overridable targets to `loadtest/k6/booking.js` with the FR-026 values as defaults — `TARGET_AVAIL_P95_MS=300`, `TARGET_AVAIL_P99_MS=1000`, `TARGET_MANIP_P95_MS=500`, `TARGET_ERROR_RATE=0.01`, mix tolerance `0.01`. Defaults are normative; overrides exist so the verdict can itself be tested
- [x] T035 [US4] Add all six thresholds to `loadtest/k6/booking.js` (`http_req_duration{flow:availability}` p95/p99, `manipulation_sequence_duration` p95, `http_req_failed` rate, `manipulation_share` bounds, `checks`) and complete the verdict in `loadtest/k6/lib/report.js`: a PASS/FAIL line per threshold and one overall verdict, relying on k6's built-in exit code 99 for a breach
- [ ] T036 [P] [US4] Check whether the running service publishes `http_server_requests_seconds_bucket`. **If it does not**, add `management.metrics.distribution.percentiles-histogram.http.server.requests: true` to `src/main/resources/application.yml` (research D8 — the only possible production-file change in this feature). If it does, record that and change nothing
- [x] T037 [US4] Add the diagnostic rows to `grafana/dashboards/mini-doodle-load-test.json`: service-side p95/p99 by `uri`, request rate by `uri`, 5xx rate, and the database-pressure row (backends, sessions by state, locks, deadlocks, rollbacks, blocks read vs hit) — `contracts/harness.md` §5
- [x] T038 [US4] Add the truthfulness panels to `grafana/dashboards/mini-doodle-load-test.json`: `k6_checks_rate` and `k6_http_req_failed_rate` beside the latency panels, so a green latency panel cannot hide a failing run

### Verification

- [ ] T039 [US4] Verify US4 independently: a run with `TARGET_AVAIL_P95_MS=1` exits **99** and prints the failing threshold; the same run with the defaults exits **0**; `echo $?` proves automated detectability (SC-005); database pressure is visible for the same window; and a saturated run shows enough to name the bottleneck (AC4)

**Checkpoint**: The run answers "is it fast enough, and where does it hurt?" rather than producing a wall of graphs.

---

## Phase 7: User Story 5 - Repeatable runs without wrecking the local environment (Priority: P2)

**Goal**: Runs are repeatable back-to-back with no manual database surgery, the normal build is unaffected, and a newcomer can learn everything from the documentation.

**Independent Test**: Run the scenario twice back-to-back and confirm comparability without manual cleanup; then run `./gradlew build --no-daemon --console=plain` and confirm its outcome and duration are unchanged.

### Implementation

- [x] T040 [P] [US5] Document the load test in `README.md`: purpose, prerequisites (Docker memory), the exact command sequence, the metric and target definitions, how to read the dashboard, and the reset/clean-slate options (SC-010)
- [x] T041 [P] [US5] Add the load test to `AGENTS.md`: the off-critical-path rule, the `loadtest/run.sh` entry point, and the never-in-CI/never-in-`check` constraint

### Verification

- [ ] T042 [US5] Verify back-to-back comparability: two full runs with the default reset between them produce the same dataset shape and a mix inside tolerance, with no manual database surgery (SC-008)
- [ ] T043 [US5] Verify isolation: `./gradlew build --no-daemon --console=plain` is green and its duration unchanged; `grep -rn "loadtest" build.gradle.kts .github .githooks` returns nothing (SC-009)

**Checkpoint**: All five stories are independently functional.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T044 [P] Reconcile the documentation with the implementation: every command in `quickstart.md` and `contracts/harness.md` must match `run.sh`'s real flags, environment variables and defaults; fix drift in either direction
- [ ] T045 Run the full `quickstart.md` procedure end-to-end as written, on a clean checkout (`docker compose down -v` first), and confirm SC-001 (one procedure, no manual dashboard editing, no manual data cleanup on re-run)
- [ ] T046 Record the measured findings of that first full run in `specs/007-k6-load-test/findings.md`: verdict, per-flow percentiles, error and refusal rates, achieved request rate, database pressure observed, and an explicit statement of which resource bound the run. This is a measurement record, not a spec change; any shortfall is reported as a finding for a separate change (spec assumption "measurement, not optimisation")
- [x] T047 [P] Final format/quality pass on what is in scope for the standard gate: `./gradlew spotlessApply` then `./gradlew check` (expected no-op for this feature — it changes no Java — but the gate must be proven green before any commit is proposed)

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 (Setup)**: no dependencies
- **Phase 2 (Foundational)**: depends on Phase 1 — **blocks every user story**
- **Phase 3–7 (Stories)**: depend on Phase 2
- **Phase 8 (Polish)**: depends on the stories being complete

### User story dependencies

All five stories are P1 except US5 (P2), but they are **not mutually independent in this feature** — measurement fidelity is inherently ordered. The honest graph is:

```
Foundational (Phase 2)
   └─→ US3  dataset at scale            (needs nothing but the stack)
          └─→ US1  run + live Grafana   (needs data to read against → MVP)
                 └─→ US2  the 5/95 mix  (extends US1's scenario with the write flow)
                        └─→ US4  verdict + DB pressure  (judges US1+US2's traffic)
                               └─→ US5  repeatability + docs  (P2)
```

- **US3 → US1**: US1's independent test requires a provisioned dataset. The `--smoke` path makes this a ~1 s dependency rather than a ~2 min one, which is why the reduced dataset is specified in T015.
- **US1 → US2**: US2 adds the manipulation flow and the mix on top of US1's scenario; both edit `loadtest/k6/booking.js`, so they cannot be parallelised.
- **US2 → US4**: the verdict judges the flows US2 completes, and its database panels are read against US1+US2's traffic.
- **US5**: last, because it certifies repeatability of everything above and documents the finished flag surface.

Where the template's "all stories in parallel after Foundational" would be dishonest, this ordering is stated instead. Within a phase, tasks proceed in ID order unless marked `[P]`.

### Same-file serialisation (do not parallelise these pairs)

| File | Tasks that edit it | Note |
| --- | --- | --- |
| `docker-compose.yml` | T004 → T007 → T008 | three separate edits to one file |
| `loadtest/run.sh` | T009 → T018 → T026 | skeleton, then provisioning, then run wiring |
| `loadtest/k6/booking.js` | T022 → T023 → T024 → T028 → T029 → T030 → T031 → T034 → T035 | the scenario grows story by story |
| `grafana/dashboards/mini-doodle-load-test.json` | T025 → T032 → T037 → T038 | panels added per story |
| `loadtest/k6/lib/report.js` | T021 → T035 | verdict added in US4 |
| `loadtest/sql/seed.sql` | T011 → T012 → T013 → T014 → T015 | built as one script |

### Parallel opportunities

- **Phase 1**: T002, T003 (different files)
- **Phase 2**: T004, T005, T006 (three different files)
- **Phase 3**: T016, T017 (two different new files) while T011–T015 proceed on `seed.sql`
- **Phase 4**: T020, T021 (two different new files) before T022 needs them
- **Phases 7–8**: T040, T041 in parallel; T044, T047 in parallel

### Parallel example: Phase 2 foundation

```bash
# Three different files, no shared edits — safe to run together:
Task: "Add the postgres-exporter service to docker-compose.yml"          # T004
Task: "Add the postgres scrape job to prometheus/prometheus.yml"         # T005
Task: "Pin the datasource UID in grafana/provisioning/datasources/..."   # T006
```

### Parallel example: Phase 3 dataset

```bash
# New, separate files — safe to write together while seed.sql is being built:
Task: "Write loadtest/sql/verify.sql asserting invariants I1–I9"         # T016
Task: "Write loadtest/sql/reset.sql (ownership-scoped delete)"           # T017
```

---

## Implementation Strategy

### MVP first (Foundational + US3 + US1)

1. Phase 1 Setup, Phase 2 Foundational — the stack is wired and both Prometheus targets are `UP`
2. Phase 3 (US3) — a dataset at the stated scale, with `verify.sql` proving it
3. Phase 4 (US1) — one command drives the availability flow and Grafana shows it live
4. **STOP and VALIDATE**: `./loadtest/run.sh --smoke`, then a full 12.5-minute run. This is already a useful deliverable: a local, watchable load test with a printed summary

### Incremental delivery

1. MVP (above) → demo: "1000 users, live in Grafana, summary printed"
2. **US2** → the workload matches the real 95/5 shape, and the mix is machine-checked
3. **US4** → the run states pass/fail and points at the bottleneck; this is where the feature starts answering the user's original question
4. **US5** → repeatable on demand, documented, provably off the build's critical path
5. **Phase 8** → the first full run's findings are recorded. **Expect the first honest run to be red**: HikariCP is at Spring Boot's default of 10 connections, and a red run is a valid outcome of this feature (spec assumption). Its fix is a separate change

### Review checkpoints

- After T010: the wiring is proven before any scenario exists
- After T019: the dataset is proven independently of k6
- After T027: the MVP loop is proven with `--smoke`
- After T033: the mix is proven machine-checked, not eyeballed
- After T039: the verdict is proven to fail *and* to pass on demand
- After T043: the build is proven untouched
- Before any commit is proposed: T047 green, and explicit approval requested

---

## Notes

- `[P]` means different files and no dependency on incomplete work — never use it for two edits to the same file
- Story labels (`[US1]`…`[US5]`) map each task to the spec's user stories for traceability
- Every story ends with a **Verification** task that is that story's independent test; a story is not done until its verification passes
- Do not start a story until its preceding checkpoint has passed
- Commit per logical group (for example: harness scaffolding, dataset, scenario, mix, verdict+db, docs), and only after asking for approval
