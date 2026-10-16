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

