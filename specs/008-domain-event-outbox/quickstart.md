# Quickstart: Domain Event Outbox

This procedure shows how to test the feature and how to see one event.

## Prerequisites

- Java 21.
- Docker is running.
- The repository root is the current directory.

## 1. Run the gates

1. Format the code.

   ```bash
   ./gradlew spotlessApply
   ```

2. Run all checks.

   ```bash
   ./gradlew check
   ```

   The build is green when the command exits with code 0. The coverage floors and the architecture
   rules run in the same command.

## 2. Start the stack

1. Start the service and the database.

   ```bash
   docker compose up --build
   ```

2. Wait for the health endpoint. The state must be `UP`.

   ```bash
   curl -s localhost:8080/actuator/health
   ```

## 3. Create one event

1. Set the caller identifier. Use the demo user from the README.

   ```bash
   export USER_ID=11111111-1111-1111-1111-111111111111
   ```

2. Create a time slot in the future.

   ```bash
   curl -s -X POST localhost:8080/api/v1/slots \
     -H "X-User-Id: $USER_ID" -H 'Content-Type: application/json' \
     -d '{"startAt":"2027-03-01T09:00:00Z","durationMinutes":30}'
   ```

   The response has code `201`. Copy the value of `id`.

3. Book a meeting on the slot. Replace `<SLOT_ID>`.

   ```bash
   curl -s -X POST localhost:8080/api/v1/slots/<SLOT_ID>/meeting \
     -H "X-User-Id: $USER_ID" -H 'Content-Type: application/json' \
     -d '{"title":"Outbox check","participants":[{"name":"P0","email":"p0@example.com"}]}'
   ```

   The response has code `201`.

## 4. Read the event record

1. Query the table.

   ```bash
   docker compose exec -T db psql -U minidoodle -d minidoodle -c \
     "SELECT id, event_type, aggregate_id, state, attempts, occurred_at
        FROM outbox_events ORDER BY id DESC LIMIT 5;"
   ```

2. Make sure that the result has these values:

   | Column | Expected value |
   | --- | --- |
   | `event_type` | `meeting.booked` |
   | `aggregate_id` | the `<SLOT_ID>` value |
   | `state` | `PUBLISHED`, after one poll interval |
   | `attempts` | `0` |

## 5. Read the log entry

1. Show the delivery entries.

   ```bash
   docker compose logs app | grep 'event=event.published' | tail -5
   ```

2. Make sure that the entry has the event identifier, the type, the aggregate identifier, and the
   time. The entry must not have a participant name or a participant email.

3. Show the payload at the debug level. Enable the level first if necessary.

   ```bash
   docker compose logs app | grep 'event=event.payload' | tail -5
   ```

## 6. Read the metrics

1. Query the Prometheus endpoint.

   ```bash
   curl -s localhost:8080/actuator/prometheus | grep outbox
   ```

2. Make sure that the output has these meters:

   | Meter | Meaning |
   | --- | --- |
   | `minidoodle_outbox_published_total` | the count of delivered events |
   | `minidoodle_outbox_failed_total` | the count of failed events |
   | `minidoodle_outbox_pending` | the count of events that wait for delivery |

## 7. Test the failure path

A delivery to the log cannot fail in a local run. The integration test `OutboxDeliveryIT` uses a
publisher that throws an error, and it checks these properties:

- the attempt count increases,
- the row stays `PENDING`,
- the 5th failure sets the `FAILED` state,
- the cleanup keeps the failed row.

Run the test directly:

```bash
./gradlew test --tests "com.minidoodle.calendar.adapter.out.outbox.OutboxDeliveryIT"
```

## Configuration

The keys are in `src/main/resources/application.yml`.

| Key | Default | Meaning |
| --- | --- | --- |
| `app.outbox.poll-interval` | `500ms` | the delay between two delivery cycles |
| `app.outbox.batch-size` | `50` | the maximum count of rows in one cycle |
| `app.outbox.max-attempts` | `5` | the count of attempts before the failed state |
| `app.outbox.cleanup-interval` | `1h` | the delay between two cleanup actions |
| `app.outbox.retention` | `7d` | the retention interval of a published row |

## Clean up

Remove the rows of this check. Replace `<SLOT_ID>`:

```bash
docker compose exec -T db psql -U minidoodle -d minidoodle -c \
  "DELETE FROM outbox_events WHERE aggregate_id = '<SLOT_ID>';"
```

Stop the stack:

```bash
docker compose down
```
