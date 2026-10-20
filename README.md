# mini-doodle

mini-doodle is a high-performance Spring Boot backend for meeting scheduling.
Each user owns a personal calendar of time slots.
Users mark slots free or busy and book meetings on free slots.

## Getting started

### Requirements

- Docker and Docker Compose

### Run locally

```bash
docker-compose up --build
```

This command starts PostgreSQL and the service.
At startup, the service applies Flyway migrations and creates a demo user.
When the stack is healthy, use these addresses:

- API base URL: `http://localhost:8080`
- OpenAPI UI: <http://localhost:8080/swagger-ui.html>
- Health: <http://localhost:8080/actuator/health>
- Metrics (Prometheus): <http://localhost:8080/actuator/prometheus>

The service uses the demo `X-User-Id` header to authenticate requests.
Use the seeded demo user:

```
X-User-Id: 11111111-1111-1111-1111-111111111111
```

See [`specs/001-time-slot-management/quickstart.md`](specs/001-time-slot-management/quickstart.md),
[`specs/002-meeting-scheduling/quickstart.md`](specs/002-meeting-scheduling/quickstart.md), and
[`specs/003-availability-query/quickstart.md`](specs/003-availability-query/quickstart.md)
for full end-to-end procedures.

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

A unique constraint limits each slot to **at most one meeting**.
While a slot holds a meeting, the service rejects `PATCH` and `DELETE` for that slot with `409 Conflict`.
Cancel the meeting before you change or delete the slot.

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

A participant can have a name, an email address, or both.
Participants do not have to be existing users.

### Availability

`GET /api/v1/availability?from=&to=` returns the caller's availability for the half-open frame `[from, to)`.
The response contains one list of intervals in time order.
The intervals do not overlap and cover the complete frame.
Each interval has one of these types:

- **`FREE`** — a slot that is not busy (`status = FREE` and no meeting),
- **`BUSY`** — a slot marked `BUSY` or holding a meeting,
- **`UNALLOCATED`** — time inside the frame covered by no slot.

The service merges adjacent intervals of the same type.
Supply `from` and `to` as ISO-8601 timestamps.
The bounds must satisfy `from < to`. The frame must not exceed 31 days.

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

Error responses use the RFC 9457 problem-details format.
The service returns `409 Conflict` for these operations:

- Create a slot that overlaps an existing slot in the same calendar.
- Book a slot that is not free.
- Change or delete a slot that holds a meeting.

The database can abort a write because of a deadlock with a concurrent write.
The service then returns `503 Service Unavailable` with `Retry-After: 1`.
Send the same request again.

## Data model

- **User**: Owns a calendar. The API identifies the user with `X-User-Id`.
- **Calendar**: An internal container for a user's time. Each user has one calendar.
- **Time slot**: A `[start, end)` interval with a configurable duration and a `FREE` or `BUSY` status.
  The database stores a `tstzrange` column.
  A GiST exclusion constraint prevents slots in the same calendar from overlapping.
- **Meeting**: A booking on a `FREE` slot with a title, optional description, and one or more free-form participants.
  `UNIQUE (slot_id)` limits a slot to one meeting.
  A foreign key with `ON DELETE RESTRICT` prevents deletion of a slot that holds a meeting.
  Cancel the meeting first.

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

Actuator exposes health, info, and Prometheus metrics.

- Slot operations emit counters and timers:
  `minidoodle.slots.created`, `minidoodle.slots.updated`, `minidoodle.slots.deleted`,
  `minidoodle.slots.create`, `minidoodle.slots.list`.
- Meeting operations emit `minidoodle.meetings.booked`, `minidoodle.meetings.viewed`,
  and `minidoodle.meetings.cancelled`, plus the `minidoodle.meetings.book`/`get`/`cancel` timers.
- Availability queries emit the `minidoodle.availability.queries` counter
  and the `minidoodle.availability.query` timer.

All operations use structured logs with `event=... key=value`.

## Testing and quality gates

The test suite uses JUnit 5, AssertJ, MockMvc, and Testcontainers with real PostgreSQL.
Start Docker before you run the tests.

```bash
./gradlew test      # run the test suite
./gradlew build     # full build (codegen + tests + boot jar)
```

`./gradlew check` runs each fast gate below.
The pre-push hook and CI enforce these gates through `./gradlew build`.
A separate CI step is not necessary.

### Coverage floors (JaCoCo)

JaCoCo reports coverage and verifies the coverage floors for production classes.
The measurements exclude generated jOOQ sources.

| Counter | Floor | Measured |
|---|---|---|
| Line | 0.95 | 99.16% |
| Branch | 0.95 | 97.22% |
| Method | 0.95 | 99.19% |
| Cyclomatic complexity | 0.95 | 97.92% |

Four classes have stricter class rules.
A bundle average can hide gaps in their error and recovery paths:

| Class | Counter | Floor |
|---|---|---|
| `GlobalExceptionHandler` | Line, Method | 1.00 |
| `JooqSlotRepository` | Line | 0.95 |
| `JooqSlotRepository` | Branch | 0.90 |
| `JooqMeetingRepository` | Line | 0.95 |
| `JooqMeetingRepository` | Branch | 0.90 |
| `CurrentUserArgumentResolver` | Branch | 1.00 |

The floors use the measured ratios, rounded **down** to the nearest multiple of 0.05.
Thus, a successful `test` run also passes verification unless coverage has decreased.
Measure coverage before you change a floor:

```bash
./gradlew test jacocoTestReport       # report -> build/reports/jacoco/test/
./gradlew jacocoTestCoverageVerification
```

Do not lower a floor to permit a regression.
Add coverage or deliberately correct the gate in the same commit.
Include the measurement.

### Architecture rules (ArchUnit)

`./gradlew test` fails if a dependency violates a layer rule.
The rules run as JUnit 5 tests:

| Rule | Meaning |
|---|---|
| R1 | No framework types (Spring, Jakarta, jOOQ, Hibernate) in the domain |
| R2 | The domain depends on the JDK and the domain only |
| R3 | The application layer does not depend on adapters. Its only non-JDK, non-domain dependencies are the Spring stereotype and transaction annotations |
| R4 | Inbound and outbound adapters do not depend on each other. Inbound adapters call application input ports, not concrete services or outbound ports |
| R5 | Shared infrastructure does not depend on the application or the adapters |
| R6 | No dependency cycles between the context's packages |

Each rule also has a fixture with a deliberate violation.
A test verifies that the rule **fails** against that fixture.
This prevents an incorrect package pattern from passing without testing any relevant type.
Failures identify the rule and the affected type.

To correct a failure, move the dependency toward the domain.
For R6, remove the dependency cycle.
See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the permitted direction.
If the rule is incorrect, change the rule and its effectiveness fixture together.

### Complexity ceilings (Checkstyle)

Four metric checks live in [`config/checkstyle/checkstyle.xml`](config/checkstyle/checkstyle.xml):
`CyclomaticComplexity` 10, `NPathComplexity` 30, `MethodLength` 70, `ParameterNumber` 7. They are
satisfied with no suppression file and no baseline.

A failure identifies the check, measured value, and ceiling.
Split the method or reduce its parameter count.
There is no suppression file or baseline.

### Mutation testing (on demand)

PIT analyzes the domain package.
It is **not** part of `check` or `build` and cannot block a push:

```bash
./gradlew pitest   # report -> build/reports/pitest/
```

PIT reports a mutation score for each package and for the complete analysis.
It also lists surviving mutations and their locations.
There is no threshold. Review survivors to find possible test gaps.

## Code style

The standard build includes formatting and lint checks.
The pre-push hook and CI enforce them through `./gradlew build`.
A separate tool step is not necessary.

- **Formatting** — Spotless with google-java-format's **AOSP** variant (4-space indent, 100-column
  limit), applied only to `src/main/java` and `src/test/java`. It owns indentation, line wrapping,
  whitespace, import order, and unused-import removal.
- **Linting** — Checkstyle with a selected ruleset in
  [`config/checkstyle/checkstyle.xml`](config/checkstyle/checkstyle.xml): naming, brace/block, and
  bug-pattern rules. It deliberately contains no formatting rules, so the two tools can never
  contradict each other.

```bash
./gradlew spotlessApply            # reformat the sources
./gradlew spotlessCheck checkstyleMain checkstyleTest   # check only (fails on any violation)
./gradlew check                    # both, as part of the normal check lifecycle
```

Both tools exclude the generated jOOQ output in `build/generated-sources`.
[`.git-blame-ignore-revs`](.git-blame-ignore-revs) records the initial format change.
This lets `git blame` show the original authors instead of the format change.
Enable it with `git config blame.ignoreRevsFile .git-blame-ignore-revs`.

## Design decisions

- **Intervals as `tstzrange`**: A GIST exclusion constraint (`btree_gist`) enforces the overlap rule in the database.
  Correctness does not depend only on application checks.
- **Hexagonal architecture**: The domain and application ports are framework-free.
  Application services keep Spring service and transaction annotations.
  Configuration and telemetry use outbound ports. Adapters contain jOOQ, Spring MVC, and Micrometer.
- **Concurrency**: The exclusion constraint controls concurrent inserts with overlapping ranges.
  If the database aborts a write because of contention, the service returns `503` with `Retry-After`.
  The service does not retry the write in that request. The client sends the request again.
  See [ADR 0001](docs/adr/0001-slot-insert-contention.md).
- **Aggregate ownership**: `TimeSlot` owns the optional `Meeting` entity and its participants.
  Domain methods enforce booking, cancellation, and change restrictions.
  The slot repository saves the complete aggregate atomically.
- **One meeting per slot**: The `uq_meetings_slot` unique constraint prevents double-booking.
  Cancellation frees the slot and deletes the meeting in the same transaction.
- **Bounded queries**: Lists use pages and a size limit.
  Range filters use the slot-range index to keep latency predictable as data grows.

## Notes

- The challenge brief references a "composer file". This is interpreted as the `docker-compose.yml`
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

This project includes a local observability stack.
The Spring Boot app exposes Micrometer's Prometheus metrics at `/actuator/prometheus`.
Prometheus collects these metrics from the app.
Grafana starts with a configured Prometheus datasource and a baseline dashboard.

### Starting the stack

```bash
docker compose up -d
```

Services:

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)
- App: http://localhost:8080

### Verifying

1. Open http://localhost:9090/targets.
2. Make sure that the `minidoodle` job is UP and collects metrics from `app:8080/actuator/prometheus`.
3. Log in to Grafana with `admin/admin`.
4. Open the "Mini Doodle Baseline" dashboard.

### Stopping and persistence

- Stop: `docker compose down`
- Data persists in named volumes: `prometheus-data` and `grafana-data`.


### Dashboards

- `Mini Doodle - Golden Signals & SLI/SLO`: traffic, latency (p95), error rate, and business metrics with SLI/SLO-oriented panels (error budget proxy).

## Load Test (1000-user booking)

The k6 load-test harness in `loadtest/` simulates 1000 concurrent users of the booking service.
Each user has up to 800 slots and up to 100 meetings.
The workload contains 95% availability checks and 5% manipulations.
A manipulation adds a slot and converts it to a meeting.
Prometheus records the results. A dedicated Grafana dashboard shows them.

### Requirements

- Docker and Docker Compose
- At least 8 GB RAM.
  The seeded dataset, in-memory Postgres, and service use a few GB.
  Use `docker compose down -v` if you need to free space.

### Quick start (smoke, proves configuration in ~1 minute)

```bash
./loadtest/run.sh --smoke
```

The smoke run provisions 10 users and checks that the system is ready.
It runs 10 virtual users (VUs) for approximately 30 s at steady state.
It prints the Grafana URL and returns k6's exit code.
A successful smoke run shows that component connections operate correctly. It does not prove performance.

### Full run (100 users, 30s ramp + 5m steady + 30s ramp-down)

```bash
./loadtest/run.sh
```

Default targets:

- Availability: p95 < 300 ms and p99 < 1000 ms.
- Manipulation sequence: p95 < 500 ms.
- Error rate: < 1%. Expected `409` and `503` responses have separate counters and are excluded from this rate.
- Manipulation mix: 5% ± 1 percentage point.

To override a default, pass an environment variable through `docker compose`.
For example, use `TARGET_AVAIL_P95_MS=...`.

### Reset and clean slate

- `./loadtest/reset.sh` — removes only the load-test data (the 10000000-... block). The demo user
  and any manually created data survive.
- `docker compose down -v` — full clean slate (drops volumes). Useful if you want to start
  completely fresh or reclaim disk space.

### Dashboard

Grafana is at `http://localhost:3000` (admin/admin). The provisioned dashboard is
`Mini Doodle - Load Test (1000 users)` (uid `minidoodle-load-test`). Select the run's `testid`
from the template variable.
The panels show these measurements:

- Client-side latency by flow and manipulation sequence latency.
- Throughput and service-side percentiles by URI.
- Database pressure: backends, sessions by state, locks, deadlocks, rollbacks, and blocks read/hit.
- Checks and http_failed rates.

### Notes

- Keep the harness outside the standard build.
  `.dockerignore` excludes `loadtest/`. `.gitignore` excludes its artifacts.
  Never reference the harness from Gradle, CI, or the pre-push hook.
  Preserve the behavior and duration of `./gradlew build`.
- `run.sh` polls `/actuator/health` for a readiness check.
  The runtime image does not contain curl.
- The seed operation uses one transaction and is idempotent through `ON CONFLICT DO NOTHING`.
  `verify.sql` makes sure that invariants hold before load starts.
  The k6 time window comes from the seeded data and uses ISO-8601 with `Z`.

## Documentation language

Write documentation in ASD-STE100 Simplified Technical English.
Before you change a README, ADR, or specification, read [the writing rules](docs/WRITING.md).
