# Delivery Log Contract

The first delivery adapter writes to the application log. A later adapter replaces it. This file
fixes the log format, so an operator and a test can find the events.

## Published event

One entry at the information level for each published event.

```text
logger: com.minidoodle.outbox
level:  INFO
text:   event=event.published event_id=<uuid> type=<event type> schema_version=1 aggregate_type=slot aggregate_id=<uuid> calendar_id=<uuid> occurred_at=<ISO-8601 instant>
```

Example:

```text
2026-10-21T09:14:03.120+02:00 INFO  [http-nio-8080-exec-1] com.minidoodle.outbox - event=event.published event_id=0f8f... type=meeting.booked schema_version=1 aggregate_type=slot aggregate_id=1c2d... calendar_id=9a7b... occurred_at=2026-10-21T07:14:03Z
```

Rules:

- Write the envelope only. The envelope has no user content (FR-010).
- Keep the field order above. A test matches the fields, not the order of the line.
- Write one entry for each event. Do not write a second entry for the same event (FR-019).

## The payload

The payload contains the meeting title, so it is not in the information-level entry. Write it at
the debug level, with the same event identifier:

```text
logger: com.minidoodle.outbox
level:  DEBUG
text:   event=event.payload event_id=<uuid> payload=<the JSON document from the schema>
```

## A failed delivery

One entry at the warning level for each failed attempt. The relay writes it, not the publisher.

```text
logger: com.minidoodle.outbox
level:  WARN
text:   event=outbox.delivery_failed event_id=<uuid> attempt=<n> error=<the error message>
```

After the 5th failed attempt, the system sets the failed state and writes no more entries for that
event.

## A cleanup run

The cleanup writes one entry at the debug level when it removes rows:

```text
logger: com.minidoodle.outbox
level:  DEBUG
text:   event=outbox.cleanup removed=<count>
```

## What a test can assert

- The count of `event=event.published` entries equals the count of published rows.
- Each `event.published` entry has the same `event_id` as its row.
- No `event=event.published` entry exists for a failed operation.
- The entry has no participant name and no participant email.
