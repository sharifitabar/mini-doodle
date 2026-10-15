# Quickstart: Meeting Scheduling

How to build, run, and verify the meeting scheduling feature locally. This builds on time slot
management — a meeting is created from an existing free slot.

## Prerequisites

- Java 21 (`java -version`)
- Docker + Docker Compose

## 1. Run the full stack

```bash
docker-compose up --build
```

This starts the service (`http://localhost:8080`) and PostgreSQL, applying Flyway migrations
(including `V2__meetings.sql`) on startup.

- OpenAPI UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/prometheus`

Seed data (dev profile) provides a demo user; its id is the value to send as `X-User-Id`.

## 2. Create a free slot to book

```bash
export USER_ID="<demo-user-id>"

curl -sS -X POST http://localhost:8080/api/v1/slots \
  -H "X-User-Id: $USER_ID" \
  -H "Content-Type: application/json" \
  -d '{"startAt":"2026-11-03T09:00:00+01:00","durationMinutes":30}'
```

Record the returned slot `id` as `SLOT_ID`.

## 3. Book a meeting from the slot

```bash
SLOT_ID="<id from step 2>"

curl -sS -X POST http://localhost:8080/api/v1/slots/$SLOT_ID/meeting \
  -H "X-User-Id: $USER_ID" \
  -H "Content-Type: application/json" \
  -d '{
        "title": "Sprint planning",
        "description": "Kick off the next sprint",
        "participants": [
          {"name": "Ada Lovelace", "email": "ada@example.com"},
          {"email": "guest@example.com"}
        ]
      }'
```

Expected: `201 Created` with the meeting body; the slot's `status` is now `BUSY`.

## 4. View and cancel

```bash
# View the meeting for the slot
curl -sS http://localhost:8080/api/v1/slots/$SLOT_ID/meeting -H "X-User-Id: $USER_ID"

# Cancel it (frees the slot; status returns to FREE)
curl -sS -o /dev/null -w '%{http_code}\n' -X DELETE \
  http://localhost:8080/api/v1/slots/$SLOT_ID/meeting -H "X-User-Id: $USER_ID"
# -> 204

# Confirm the slot is free again
curl -sS http://localhost:8080/api/v1/slots/$SLOT_ID -H "X-User-Id: $USER_ID"
```

## 5. Verify the key rules

```bash
# a) A slot can hold only one meeting -> second booking is 409
curl -sS -X POST http://localhost:8080/api/v1/slots/$SLOT_ID/meeting \
  -H "X-User-Id: $USER_ID" -H "Content-Type: application/json" \
  -d '{"title":"Another","participants":[{"name":"Bob"}]}'
# -> 409 conflict

# b) A booked slot cannot be modified, deleted, or re-marked while it holds a meeting
#    (re-book first, then try each of the slot operations) -> 409 each
curl -sS -X PATCH http://localhost:8080/api/v1/slots/$SLOT_ID \
  -H "X-User-Id: $USER_ID" -H "Content-Type: application/json" \
  -d '{"status":"FREE"}'
# -> 409 conflict
```

## 6. Run the tests

```bash
./gradlew test
```

The suite runs:
- domain unit tests (meeting title and participant invariants),
- integration tests against a real PostgreSQL via Testcontainers (one-meeting-per-slot unique
  constraint, slot-deletion FK protection, participant cascade, concurrent booking),
- MockMvc contract tests (book/view/cancel status codes, validation, ownership).

## 7. Troubleshooting

- **`409` on booking a free-looking slot**: the slot may already hold a meeting, or its status
  is `BUSY`. View it with `GET /api/v1/slots/{slotId}` and cancel the meeting if needed.
- **`404` for a meeting**: the slot does not exist, is not in your calendar, or holds no
  meeting (the API does not disclose another user's slot).
- **`400` on booking**: the title is blank/too long or no valid participant was supplied
  (each participant needs a non-blank `name` or `email`).
