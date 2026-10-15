# mini-doodle

A high-performance Spring Boot backend for meeting scheduling: users own a personal calendar of
time slots, mark those slots free or busy, and (on the roadmap) book them as meetings.

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

See [`specs/001-time-slot-management/quickstart.md`](specs/001-time-slot-management/quickstart.md)
for a full end-to-end walkthrough.

## API

All endpoints are under `/api/v1` and require the `X-User-Id` header.

| Method   | Path                  | Description                                       |
|----------|-----------------------|---------------------------------------------------|
| `POST`   | `/slots`              | Create a time slot (starts `FREE`)                |
| `GET`    | `/slots`              | List the caller's slots (filter + paginate)       |
| `GET`    | `/slots/{slotId}`     | Fetch one slot                                    |
| `PATCH`  | `/slots/{slotId}`     | Update `status`, `startAt`, and/or `durationMinutes` |
| `DELETE` | `/slots/{slotId}`     | Delete a slot                                     |

Example:

```bash
export USER_ID=11111111-1111-1111-1111-111111111111

curl -sS -X POST http://localhost:8080/api/v1/slots \
  -H "X-User-Id: $USER_ID" -H "Content-Type: application/json" \
  -d '{"startAt":"2026-11-02T09:00:00Z","durationMinutes":30}'
```

Error responses use the RFC 9457 problem-details shape. A slot that overlaps an existing slot in
the same calendar is rejected with `409 Conflict`.

## Data model

- **User** — owns a calendar; identified in the API by `X-User-Id`.
- **Calendar** — a user's personal time container (one per user; internal concept).
- **Time slot** — a `[start, end)` interval with a configurable duration and a `FREE`/`BUSY`
  status. Stored with a `tstzrange` column and a GiST exclusion constraint that makes overlapping
  slots in the same calendar impossible at the database level.

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
`minidoodle.slots.create`, `minidoodle.slots.list`) and structured `event=... key=value` logs.

## Testing

The suite uses JUnit 5, AssertJ, MockMvc, and Testcontainers (real PostgreSQL); Docker must be
running.

```bash
./gradlew test      # run the test suite
./gradlew build     # full build (codegen + tests + boot jar)
```

## Design decisions

- **Intervals as `tstzrange`**: overlap is a first-class DB concern enforced by a GIST exclusion
  constraint (`btree_gist`), so correctness does not rely on application-level checks alone.
- **Hexagonal architecture**: domain and application layers are framework-free; jOOQ, Spring MVC,
  and Micrometer live in adapters.
- **Concurrency**: overlapping concurrent inserts are retried on deadlock/serialization errors and
  eventually surface as a `409`.
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
- [ ] Meeting scheduling from slots
- [ ] Availability query with aggregated view
