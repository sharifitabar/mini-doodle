# Quickstart: Availability Query

How to build, run, and verify the availability query feature locally. It builds on time slot
management (and meeting scheduling): availability is derived from the caller's slots and meetings.

## Prerequisites

- Java 21 (`java -version`)
- Docker + Docker Compose

## 1. Run the full stack

```bash
docker-compose up --build
```

This starts the service (`http://localhost:8080`) and PostgreSQL, applying Flyway migrations on
startup (no new migration is added by this feature).

- OpenAPI UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/prometheus`

Seed data (dev profile) provides a demo user; its id is the value to send as `X-User-Id`.

## 2. Create some slots to shape availability

```bash
export USER_ID="<demo-user-id>"

# Two contiguous FREE slots (09:00–10:00) that should merge into one free window
curl -sS -X POST http://localhost:8080/api/v1/slots -H "X-User-Id: $USER_ID" \
  -H "Content-Type: application/json" \
  -d '{"startAt":"2026-11-02T09:00:00Z","durationMinutes":30}'
curl -sS -X POST http://localhost:8080/api/v1/slots -H "X-User-Id: $USER_ID" \
  -H "Content-Type: application/json" \
  -d '{"startAt":"2026-11-02T09:30:00Z","durationMinutes":30}'

# A BUSY slot (10:00–10:30) right after the free window
curl -sS -X POST http://localhost:8080/api/v1/slots -H "X-User-Id: $USER_ID" \
  -H "Content-Type: application/json" \
  -d '{"startAt":"2026-11-02T10:00:00Z","durationMinutes":30}'
# then mark it busy (PATCH /api/v1/slots/{slotId} with {"status":"BUSY"})
```

## 3. Query availability over a frame

```bash
curl -sS "http://localhost:8080/api/v1/availability?from=2026-11-02T00:00:00Z&to=2026-11-02T23:59:59Z" \
  -H "X-User-Id: $USER_ID"
```

Expected `200 OK` with an ordered, gap-free interval list, for example:

```json
{
  "from": "2026-11-02T00:00:00Z",
  "to": "2026-11-02T23:59:59Z",
  "intervals": [
    { "startAt": "2026-11-02T00:00:00Z", "endAt": "2026-11-02T09:00:00Z", "kind": "UNALLOCATED" },
    { "startAt": "2026-11-02T09:00:00Z", "endAt": "2026-11-02T10:00:00Z", "kind": "FREE" },
    { "startAt": "2026-11-02T10:00:00Z", "endAt": "2026-11-02T10:30:00Z", "kind": "BUSY" },
    { "startAt": "2026-11-02T10:30:00Z", "endAt": "2026-11-02T23:59:59Z", "kind": "UNALLOCATED" }
  ]
}
```

The two contiguous free slots appear as a **single** `FREE` window, and the free and busy intervals
meet without merging.

## 4. Meetings count as busy

Book a meeting on a free slot and re-run the query:

```bash
curl -sS -X POST http://localhost:8080/api/v1/slots/$SLOT_ID/meeting -H "X-User-Id: $USER_ID" \
  -H "Content-Type: application/json" -d '{"title":"Standup","participants":[{"name":"Ada"}]}'

curl -sS "http://localhost:8080/api/v1/availability?from=2026-11-02T09:00:00Z&to=2026-11-02T10:00:00Z" \
  -H "X-User-Id: $USER_ID"
# -> the booked slot is now reported as BUSY
```

## 5. Verify the key rules

```bash
# a) Inverted frame -> 400
curl -sS -o /dev/null -w '%{http_code}\n' \
  "http://localhost:8080/api/v1/availability?from=2026-11-02T10:00:00Z&to=2026-11-02T09:00:00Z" \
  -H "X-User-Id: $USER_ID"
# -> 400

# b) Frame longer than 31 days -> 400
curl -sS -o /dev/null -w '%{http_code}\n' \
  "http://localhost:8080/api/v1/availability?from=2026-01-01T00:00:00Z&to=2026-12-31T00:00:00Z" \
  -H "X-User-Id: $USER_ID"
# -> 400

# c) Missing frame parameter -> 400
curl -sS -o /dev/null -w '%{http_code}\n' \
  "http://localhost:8080/api/v1/availability?from=2026-11-02T00:00:00Z" \
  -H "X-User-Id: $USER_ID"
# -> 400

# d) Unknown caller -> 403
curl -sS -o /dev/null -w '%{http_code}\n' \
  "http://localhost:8080/api/v1/availability?from=2026-11-02T00:00:00Z&to=2026-11-03T00:00:00Z" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000000"
# -> 403
```

## 6. Run the tests

```bash
./gradlew test
```

The suite runs:
- domain unit tests (`AvailabilityCalculator`: merging, clipping, gaps, empty frame, free/busy
  boundary; `TimeFrame` bounds; interval invariants),
- a Testcontainers integration test for the range query and the slot↔meeting join, including
  ownership isolation,
- a MockMvc contract test (status codes, response shape, frame validation, auth).

## 6. Troubleshooting

- **`400` on a valid-looking frame**: check for a missing bound, an end not after the start, or a
  span longer than 31 days; times must include an offset.
- **`FREE` window looks "split"**: only *contiguous* free slots are merged; a busy slot or an
  unallocated gap between them keeps them separate.
- **Time appears `UNALLOCATED`**: no slot covers it — the service does not treat undeclared time as
  available.
