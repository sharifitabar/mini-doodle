# mini-doodle

A high-performance Spring Boot backend for meeting scheduling: users own a personal calendar of
time slots, mark those slots free or busy, and book a free slot as a meeting.

## Getting started

### Prerequisites

- Docker and Docker Compose

### Run locally

```bash
docker-compose up --build
```

This starts PostgreSQL and the service, applying Flyway migrations and seeding a demo user on
startup. Once healthy:

- API base URL: `http://localhost:8080`
- OpenAPI UI: <http://localhost:8080/swagger-ui.html>
- Health: <http://localhost:8080/actuator/health>
- Metrics (Prometheus): <http://localhost:8080/actuator/prometheus>

Requests are authenticated by a demo `X-User-Id` header. Use the seeded demo user:

```
X-User-Id: 11111111-1111-1111-1111-111111111111
```

See [`specs/001-time-slot-management/quickstart.md`](specs/001-time-slot-management/quickstart.md),
[`specs/002-meeting-scheduling/quickstart.md`](specs/002-meeting-scheduling/quickstart.md), and
[`specs/003-availability-query/quickstart.md`](specs/003-availability-query/quickstart.md)
for full end-to-end walkthroughs.

## API

All endpoints are under `/api/v1` and require the `X-User-Id` header.

| Method   | Path                        | Description                                       |
|----------|-----------------------------|---------------------------------------------------|
| `POST`   | `/slots`                    | Create a time slot (starts `FREE`)                |
| `GET`    | `/slots`                    | List the caller's slots (filter + paginate)       |
| `GET`    | `/slots/{slotId}`           | Fetch one slot                                    |
| `PATCH`  | `/slots/{slotId}`           | Update `status`, `startAt`, and/or `durationMinutes` |
| `DELETE` | `/slots/{slotId}`           | Delete a slot                                     |
| `POST`   | `/slots/{slotId}/meeting`   | Book a meeting on a `FREE` slot (slot becomes `BUSY`) |
| `GET`    | `/slots/{slotId}/meeting`   | Fetch the meeting on a slot                       |
| `DELETE` | `/slots/{slotId}/meeting`   | Cancel a meeting (slot returns to `FREE`)         |
| `GET`    | `/availability`             | Aggregated free/busy/unallocated view over a frame |

A slot holds **at most one meeting** (enforced by a unique constraint). While a slot holds a
meeting, `PATCH`/`DELETE` on that slot are rejected with `409 Conflict` until the meeting is
cancelled.

Example — create a slot and book a meeting on it:

```bash
export USER_ID=11111111-1111-1111-1111-111111111111

SLOT_ID=$(curl -sS -X POST http://localhost:8080/api/v1/slots \
  -H "X-User-Id: $USER_ID" -H "Content-Type: application/json" \
  -d '{"startAt":"2026-11-02T09:00:00Z","durationMinutes":30}' | jq -r .id)

curl -sS -X POST "http://localhost:8080/api/v1/slots/$SLOT_ID/meeting" \
  -H "X-User-Id: $USER_ID" -H "Content-Type: application/json" \
  -d '{"title":"Sprint planning","participants":[{"name":"Ada Lovelace","email":"ada@example.com"}]}'
```

Participants are free-form (a name and/or email); they need not be existing users.

### Availability

`GET /api/v1/availability?from=&to=` returns the caller's availability over the half-open frame
`[from, to)` as one chronologically ordered list of disjoint intervals that cover the frame exactly.
Each interval is classified as:

- **`FREE`** — a slot that is not busy (`status = FREE` and no meeting),
- **`BUSY`** — a slot marked `BUSY` or holding a meeting,
- **`UNALLOCATED`** — time inside the frame covered by no slot.

Contiguous intervals of the same kind are merged. `from` and `to` are required ISO-8601 timestamps
with `from < to` and a span of at most 31 days.

```bash
curl -sS "http://localhost:8080/api/v1/availability?from=2026-11-02T09:00:00Z&to=2026-11-02T17:00:00Z" \
  -H "X-User-Id: $USER_ID"
```

```json
{
  "from": "2026-11-02T09:00:00Z",
  "to": "2026-11-02T17:00:00Z",
  "intervals": [
    { "startAt": "2026-11-02T09:00:00Z", "endAt": "2026-11-02T10:00:00Z", "kind": "FREE" },
    { "startAt": "2026-11-02T10:00:00Z", "endAt": "2026-11-02T11:00:00Z", "kind": "BUSY" },
    { "startAt": "2026-11-02T11:00:00Z", "endAt": "2026-11-02T17:00:00Z", "kind": "UNALLOCATED" }
  ]
}
```

Error responses use the RFC 9457 problem-details shape. A slot that overlaps an existing slot in
the same calendar, booking a slot that is not free, or modifying/deleting a slot that holds a
meeting are all rejected with `409 Conflict`.

## Data model

- **User** — owns a calendar; identified in the API by `X-User-Id`.
- **Calendar** — a user's personal time container (one per user; internal concept).
- **Time slot** — a `[start, end)` interval with a configurable duration and a `FREE`/`BUSY`
  status. Stored with a `tstzrange` column and a GiST exclusion constraint that makes overlapping
  slots in the same calendar impossible at the database level.
- **Meeting** — a booking on a `FREE` slot: a title, optional description, and one or more
  free-form participants. A slot has at most one meeting (`UNIQUE (slot_id)`); deleting a slot
  that holds a meeting is blocked by a foreign key (`ON DELETE RESTRICT`), so a meeting must be
  cancelled first.

## Configuration

| Variable                    | Default                                          | Purpose                          |
|-----------------------------|--------------------------------------------------|----------------------------------|
| `DB_URL`                    | `jdbc:postgresql://localhost:5432/minidoodle`    | JDBC URL                         |
| `DB_USER` / `DB_PASSWORD`   | `minidoodle` / `minidoodle`                      | Database credentials             |
| `SERVER_PORT`               | `8080`                                           | HTTP port                        |
| `SEED_DEMO_DATA`            | `true`                                           | Seed the demo user on startup    |
| `SLOT_MAX_DURATION_MINUTES` | `1440`                                           | Maximum slot duration            |

The `docker` Spring profile (used by Compose) points the datasource at the `db` service.

## Observability

Actuator exposes health, info, and Prometheus metrics. Slot operations emit counters and timers
(`minidoodle.slots.created`, `minidoodle.slots.updated`, `minidoodle.slots.deleted`,
`minidoodle.slots.create`, `minidoodle.slots.list`); meeting operations emit
`minidoodle.meetings.booked`, `minidoodle.meetings.viewed`, `minidoodle.meetings.cancelled` and the
`minidoodle.meetings.book`/`get`/`cancel` timers. Availability queries emit the
`minidoodle.availability.queries` counter and the `minidoodle.availability.query` timer. All use
structured `event=... key=value` logs.

## Testing and quality gates

The suite uses JUnit 5, AssertJ, MockMvc, and Testcontainers (real PostgreSQL); Docker must be
running.

```bash
./gradlew test      # run the test suite
./gradlew build     # full build (codegen + tests + boot jar)
```

Every fast gate below rides `./gradlew check`, so the same `./gradlew build` used by the pre-push
hook and CI enforces all of it. Nothing here needs a separate CI step.

### Coverage floors (JaCoCo)

Coverage is not just reported — it is verified. Floors apply to production classes with jOOQ's
generated sources excluded.

| Counter | Floor | Measured |
|---|---|---|
| Line | 0.95 | 99.07% |
| Branch | 0.95 | 96.95% |
| Method | 0.95 | 99.12% |
| Cyclomatic complexity | 0.95 | 97.74% |

Three classes carry stricter, class-scoped floors, because they hold the error and recovery
behaviour that a bundle average hides:

| Class | Counter | Floor |
|---|---|---|
| `GlobalExceptionHandler` | Line, Method | 1.00 |
| `JooqSlotRepository` | Line | 0.95 |
| `JooqSlotRepository` | Branch | 0.90 |
| `CurrentUserArgumentResolver` | Branch | 1.00 |

The floors are the ratios the suite actually achieves, rounded **down** to the nearest 0.05, so a
green `test` run is always a green verification and only a real regression can trip one. Measure
before changing them:

```bash
./gradlew test jacocoTestReport       # report -> build/reports/jacoco/test/
./gradlew jacocoTestCoverageVerification
```

Do not lower a floor to accommodate a regression; raise the coverage or fix the gate deliberately,
in the same commit, with the measurement beside it.

### Architecture rules (ArchUnit)

`./gradlew test` fails if the layering slips. The rules are ordinary JUnit 5 tests:

| Rule | Meaning |
|---|---|
| R1 | No framework types (Spring, Jakarta, jOOQ, Hibernate) in the domain |
| R2 | The domain depends on the JDK and the domain only |
| R3 | The application layer does not depend on adapters |
| R4 | Inbound and outbound adapters do not depend on each other |
| R5 | Shared infrastructure does not depend on the application or the adapters |
| R6 | No dependency cycles between the context's packages |

Each rule is also asserted to **fail** against a deliberately violating fixture, so a broken package
pattern cannot produce a rule that is vacuously green. Failures name the rule and the offending type.

### Complexity ceilings (Checkstyle)

Four metric checks live in [`config/checkstyle/checkstyle.xml`](config/checkstyle/checkstyle.xml):
`CyclomaticComplexity` 10, `NPathComplexity` 30, `MethodLength` 70, `ParameterNumber` 7. They are
satisfied with no suppression file and no baseline.

### Mutation testing (on demand)

PIT is scoped to the domain package and is deliberately **not** part of `check` or `build`, so it
can never block a push:

```bash
./gradlew pitest   # report -> build/reports/pitest/
```

It reports a per-package and overall mutation score, plus a list of surviving mutations with their
locations. There is no threshold: survivors are a triage signal, not a gate.

## Code style

Formatting and linting are part of the standard build, so they are enforced by whatever runs
`./gradlew build` — including the pre-push hook and CI. No separate tooling step is needed.

- **Formatting** — Spotless with google-java-format's **AOSP** variant (4-space indent, 100-column
  limit), applied only to `src/main/java` and `src/test/java`. It owns indentation, line wrapping,
  whitespace, import order, and unused-import removal.
- **Linting** — Checkstyle with a curated ruleset in
  [`config/checkstyle/checkstyle.xml`](config/checkstyle/checkstyle.xml): naming, brace/block, and
  bug-pattern rules. It deliberately contains no formatting rules, so the two tools can never
  contradict each other.

```bash
./gradlew spotlessApply            # reformat the sources
./gradlew spotlessCheck checkstyleMain checkstyleTest   # check only (fails on any violation)
./gradlew check                    # both, as part of the normal check lifecycle
```

The jOOQ output under `build/generated-sources` is machine-generated and is excluded from both
tools. The one-time reformat that introduced the gate is recorded in
[`.git-blame-ignore-revs`](.git-blame-ignore-revs) so it does not pollute `git blame`; enable it with
`git config blame.ignoreRevsFile .git-blame-ignore-revs`.

## Design decisions

- **Intervals as `tstzrange`**: overlap is a first-class DB concern enforced by a GIST exclusion
  constraint (`btree_gist`), so correctness does not rely on application-level checks alone.
- **Hexagonal architecture**: domain and application layers are framework-free; jOOQ, Spring MVC,
  and Micrometer live in adapters.
- **Concurrency**: overlapping concurrent inserts are retried on deadlock/serialization errors and
  eventually surface as a `409`.
- **One meeting per slot**: the `uq_meetings_slot` unique constraint is the authoritative guard
  against double-booking, and cancellation frees the slot in the same transaction that deletes the
  meeting.
- **Bounded queries**: listing is paginated and capped, and range filters use the slot-range index
  for predictable latency as data grows.

## Notes

- The challenge brief references a "composer file"; this is interpreted as the `docker-compose.yml`
  that defines all service dependencies for the service.

## Roadmap

- [x] Project skeleton (Spring Boot + build tool)
- [x] Docker Compose environment (app + PostgreSQL)
- [x] Time slot CRUD and status management
- [x] Tests
- [x] Metrics and observability
- [x] API documentation (OpenAPI)
- [x] Meeting scheduling (book/view/cancel on a slot)
- [x] Availability query with aggregated view

## Metrics Stack (Prometheus + Grafana)

This project includes a local observability stack. The Spring Boot app exposes Prometheus metrics at `/actuator/prometheus` (Micrometer). Prometheus scrapes the app, and Grafana is pre-provisioned with Prometheus as the datasource and a baseline dashboard.

### Starting the stack

```bash
docker compose up -d
```

Services:
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)
- App: http://localhost:8080

### Verifying

1. Prometheus targets: http://localhost:9090/targets — `minidoodle` job should be UP (scraping `app:8080/actuator/prometheus`).
2. Grafana: log in with `admin/admin`, open "Mini Doodle Baseline" dashboard.

### Stopping and persistence

- Stop: `docker compose down`
- Data persists in named volumes: `prometheus-data` and `grafana-data`.


### Dashboards

- `Mini Doodle - Golden Signals & SLI/SLO`: traffic, latency (p95), error rate, and business metrics with SLI/SLO-oriented panels (error budget proxy).

## Load Test (1000-user booking)

The repository contains a k6 load-test harness in `loadtest/` to simulate 1000 concurrent users
(each with up to 800 slots and up to 100 meetings) against the booking service. The workload
is 95% availability checks and 5% manipulations (add a slot → convert it to a meeting). Results
are recorded in Prometheus and visualised in a dedicated Grafana dashboard.

### Prerequisites

- Docker and Docker Compose
- At least 8 GB RAM (the seeded dataset and the in-memory Postgres + service use a few GB;
  `docker compose down -v` frees space if you need it)

### Quick start (smoke, proves wiring in ~1 minute)

```bash
./loadtest/run.sh --smoke
```

This provisions 10 users, runs 10 VUs for ~30 s steady state, enforces readiness, prints the
Grafana URL, and exits with k6's exit code. A green smoke run proves plumbing; it does not prove
performance.

### Full run (100 users, 30s ramp + 5m steady + 30s ramp-down)

```bash
./loadtest/run.sh
```

Default targets: availability p95 < 300 ms, p99 < 1000 ms; manipulation sequence p95 < 500 ms;
error rate < 1% (business refusals 409 are excluded); mix 5% ± 1 pt. Defaults can be overridden
via environment variables passed through `docker compose` (e.g. `TARGET_AVAIL_P95_MS=...`).

### Reset and clean slate

- `./loadtest/reset.sh` — removes only the load-test data (the 10000000-... block). The demo user
  and any hand-made data survive.
- `docker compose down -v` — full clean slate (drops volumes). Useful if you want to start
  completely fresh or reclaim disk space.

### Dashboard

Grafana is at `http://localhost:3000` (admin/admin). The provisioned dashboard is
`Mini Doodle - Load Test (1000 users)` (uid `minidoodle-load-test`). Select the run's `testid`
from the template variable; panels show client-side latency (by flow), manipulation sequence
latency, throughput, service-side percentiles by URI, database pressure (backends, sessions by
state, locks, deadlocks, rollbacks, blocks read/hit), and checks + http_failed rates.

### Notes

- The harness is off the critical path: `loadtest/` is `.dockerignore`-d, `.gitignore`'d for
  artifacts, and must never be referenced by Gradle, CI, or the pre-push hook. `./gradlew build`
  must remain unchanged in behaviour/duration.
- Readiness is enforced by `run.sh` polling `/actuator/health` (no curl in the runtime image).
- Seed runs inside a single transaction, is idempotent (`ON CONFLICT DO NOTHING`), and `verify.sql`
  enforces invariants before load starts. The window passed to k6 is derived from seeded data and
  formatted as ISO-8601 with `Z`.
