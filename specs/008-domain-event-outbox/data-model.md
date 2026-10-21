# Data Model: Domain Event Outbox

The feature adds one table and one domain package. It changes no existing table.

## Table `outbox_events`

The migration is `V4__outbox_events.sql`. Research decision D1 gives the DDL.

| Column | Type | Rule |
| --- | --- | --- |
| `id` | `BIGSERIAL` | Primary key. It gives the capture order. |
| `event_id` | `UUID` | Unique. The identifier of the event. |
| `event_type` | `TEXT` | One of the 5 types. A check constraint limits the values. |
| `schema_version` | `INTEGER` | 1 for this feature. A check constraint requires 1 or more. |
| `aggregate_type` | `TEXT` | `slot` for all 5 types. |
| `aggregate_id` | `UUID` | The time slot identifier. |
| `calendar_id` | `UUID` | The calendar of the caller. |
| `occurred_at` | `TIMESTAMPTZ` | The time of the state change, from the service clock. |
| `payload` | `JSONB` | The changed facts. A check constraint requires a JSON object. |
| `state` | `TEXT` | `PENDING`, `PUBLISHED`, or `FAILED`. |
| `attempts` | `INTEGER` | The count of failed delivery attempts. |
| `last_error` | `TEXT` | The message of the last failure. |
| `published_at` | `TIMESTAMPTZ` | The time of the successful delivery. |
| `failed_at` | `TIMESTAMPTZ` | The time of the 5th failure. |
| `created_at` | `TIMESTAMPTZ` | The time of the insert. |

### Indexes

| Index | Definition | Serves |
| --- | --- | --- |
| `ix_outbox_pending` | `(id) WHERE state = 'PENDING'` | the poll query, in capture order |
| `ix_outbox_published_at` | `(published_at) WHERE state = 'PUBLISHED'` | the cleanup query |
| `uq_outbox_event_id` | `UNIQUE (event_id)` | duplicate protection |

## Delivery state

```text
PENDING --delivery ok--------------------> PUBLISHED --cleanup after 7 days--> removed
PENDING --failure, attempts < 5----------> PENDING
PENDING --failure, attempts = 5----------> FAILED    --stays for the operator--
```

The system sets `state` and the matching timestamp in one statement. A row in `PUBLISHED` has
`published_at`. A row in `FAILED` has `failed_at`. The code path prevents a different combination.
The database checks the value set, not the pair.

## Domain events

Package `com.minidoodle.calendar.domain.event`. The sealed interface `CalendarEvent` has these
members:

| Member | Type | Meaning |
| --- | --- | --- |
| `eventId` | `UUID` | the unique identifier |
| `type()` | `String` | one of the 5 type names |
| `schemaVersion()` | `int` | 1 |
| `aggregateType()` | `String` | `slot` |
| `aggregateId()` | `UUID` | the time slot identifier |
| `calendarId` | `UUID` | the calendar identifier |
| `occurredAt` | `OffsetDateTime` | the time of the change |

### Payload documents

The envelope columns hold the values above. The payload holds the changed facts only:

| Event type | Payload fields |
| --- | --- |
| `slot.created` | `start_at`, `duration_minutes`, `status` |
| `slot.updated` | `start_at`, `duration_minutes`, `status`, `changed` (array of field names) |
| `slot.deleted` | `start_at`, `duration_minutes`, `status` (the last values) |
| `meeting.booked` | `meeting_id`, `title`, `participant_count` |
| `meeting.cancelled` | `meeting_id` |

The payload has no participant name and no participant email (FR-007).

### The aggregate records the events

`TimeSlot` gets a private `List<CalendarEvent> events`. A domain method adds an event only after a
real change:

| Method | Event | Condition |
| --- | --- | --- |
| `book(...)` | `MeetingBooked` | the booking succeeds |
| `cancelMeeting(now)` | `MeetingCancelled` | a meeting exists |
| `changeTime(range, now)` | `SlotUpdated` | the new range is not equal to the old range |
| `changeStatus(status, now)` | `SlotUpdated` | the new status is not equal to the old status |
| `recordDeletion(now)` | `SlotDeleted` | the delete use case calls it |

`TimeSlot.create(...)` adds `SlotCreated`. `drainEvents()` returns a copy and clears the list, so
a second call returns no event.

## Validation rules

| Rule | Source | Where |
| --- | --- | --- |
| exactly one event for each successful change | FR-001 | aggregate |
| no event for a read | FR-002 | no aggregate method runs |
| no event for an equal value | FR-003 | the aggregate compares the values |
| the record and the state change are one unit | FR-004 | one transaction |
| the envelope has all 6 fields plus the time | FR-005 | the event records and the table |
| the slot is the aggregate | FR-006 | the event constructors |
| no participant name or email | FR-007 | the `MeetingBooked` record |
| one log entry for each delivered event | FR-009 | the publisher adapter |

## Relationships

- One calendar has many event records. `calendar_id` is not a foreign key: the record must stay
  valid after a calendar is removed, and the feature must not add a new delete restriction.
- One time slot has many event records. `aggregate_id` is not a foreign key, for the same reason:
  a `slot.deleted` event outlives its slot.
- A meeting appears only in the payload of a `meeting.booked` or a `meeting.cancelled` event.
  Its title is in the payload. It has no separate table relation.
