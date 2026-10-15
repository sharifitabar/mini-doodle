# Quickstart: Time Slot Management

How to build, run, and verify the time slot feature locally.

## Prerequisites

- Java 21 (`java -version`)
- Docker + Docker Compose

## 1. Run the full stack

```bash
docker-compose up --build
```

This starts the service (`http://localhost:8080`) and PostgreSQL, applying Flyway
migrations on startup.

- OpenAPI UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/prometheus`

Seed data (dev profile) provides a demo user; its id is the value to send as `X-User-Id`.

## 2. Create a slot

```bash
export USER_ID="<demo-user-id>"

curl -sS -X POST http://localhost:8080/api/v1/slots \
  -H "X-User-Id: $USER_ID" \
  -H "Content-Type: application/json" \
  -d '{"startAt":"2026-11-02T09:00:00+01:00","durationMinutes":30}'
```

Expected: `201 Created` with a slot body whose `status` is `FREE`.

## 3. Exercise the lifecycle

```bash
SLOT_ID="<id from step 2>"

# List slots (with optional filters)
curl -sS "http://localhost:8080/api/v1/slots?limit=50" -H "X-User-Id: $USER_ID"

# Mark busy
curl -sS -X PATCH http://localhost:8080/api/v1/slots/$SLOT_ID \
  -H "X-User-Id: $USER_ID" -H "Content-Type: application/json" \
  -d '{"status":"BUSY"}'

# Modify time
curl -sS -X PATCH http://localhost:8080/api/v1/slots/$SLOT_ID \
  -H "X-User-Id: $USER_ID" -H "Content-Type: application/json" \
  -d '{"startAt":"2026-11-02T10:00:00+01:00","durationMinutes":45}'

# Delete
curl -sS -o /dev/null -w '%{http_code}\n' -X DELETE \
  http://localhost:8080/api/v1/slots/$SLOT_ID -H "X-User-Id: $USER_ID"
```

## 4. Verify the key rule: no overlaps

Creating a slot that overlaps an existing one must fail with `409 Conflict`.
Note that step 3 moved the slot to 10:00 for 45 minutes, so the overlapping
request must target that window (a 09:15 start would no longer overlap):

```bash
curl -sS -X POST http://localhost:8080/api/v1/slots \
  -H "X-User-Id: $USER_ID" -H "Content-Type: application/json" \
  -d '{"startAt":"2026-11-02T10:15:00+01:00","durationMinutes":30}'
# -> 409, problem+json explaining the overlap
```

## 5. Run the tests

```bash
./gradlew test
```

The suite runs:
- domain unit tests (overlap/duration/status rules),
- integration tests against a real PostgreSQL via Testcontainers (exclusion-constraint and
  concurrency behavior),
- MockMvc contract tests (status codes, validation, ownership).

## 6. Troubleshooting

- **`extension "btree_gist" is not available`**: ensure the PostgreSQL image allows
  `CREATE EXTENSION btree_gist` (the official `postgres` image does) and that Flyway
  `V1__init.sql` ran (check `actuator/health` and logs).
- **`403` on every request**: the `X-User-Id` header is missing or unknown; use the seeded
  demo user id.
- **Port 8080 in use**: change the published port in `docker-compose.yml`.
