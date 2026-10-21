# Phase 0 Research: Domain Event Outbox

All Technical Context unknowns are resolved below. Each decision obeys the constraints in
[plan.md](plan.md) and the constitution.

## D1 — The outbox table is one table with a delivery state

**Decision**: add `V4__outbox_events.sql`:

```sql
CREATE TABLE outbox_events (
    id             BIGSERIAL PRIMARY KEY,
    event_id       UUID        NOT NULL,
    event_type     TEXT        NOT NULL,
    schema_version INTEGER     NOT NULL,
    aggregate_type TEXT        NOT NULL,
    aggregate_id   UUID        NOT NULL,
    calendar_id    UUID,
    occurred_at    TIMESTAMPTZ NOT NULL,
    payload        JSONB       NOT NULL,
    state          TEXT        NOT NULL DEFAULT 'PENDING',
    attempts       INTEGER     NOT NULL DEFAULT 0,
    last_error     TEXT,
    published_at   TIMESTAMPTZ,
    failed_at      TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id),
    CONSTRAINT ck_outbox_state CHECK (state IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_outbox_schema_version CHECK (schema_version >= 1),
    CONSTRAINT ck_outbox_event_type CHECK (event_type IN
        ('slot.created', 'slot.updated', 'slot.deleted',
         'meeting.booked', 'meeting.cancelled')),
    CONSTRAINT ck_outbox_payload_object CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX ix_outbox_pending ON outbox_events (id) WHERE state = 'PENDING';
CREATE INDEX ix_outbox_published_at
    ON outbox_events (published_at) WHERE state = 'PUBLISHED';
```

**Rationale**: the row is the durable record. `id` gives the capture order. `state` gives one
source of truth for delivery. The two partial indexes serve the poll query and the cleanup query.
`BIGSERIAL` is monotonic within one database, so `ORDER BY id` keeps the order of one slot.

**Alternatives considered**: two tables (events plus delivery) — more joins and no gain. A
`published` boolean — cannot express the failed state. Deleting rows after delivery — loses the
record that the specification requires.

## D2 — The payload is JSONB, and the adapter serializes it

**Decision**: the envelope is in columns. The changed facts are one `payload` document of type
`jsonb`. `JooqEventOutbox` serializes a domain event with Jackson into that document. jOOQ maps
`jsonb` to `org.jooq.JSONB`. The adapter calls `JSONB.valueOf(json)`.

**Rationale**: the application layer may not use Jackson or jOOQ (ArchUnit rule
`APPLICATION_DEPENDENCIES_ARE_CLOSED`). The adapter owns the format. `jsonb` validates the
document and lets a later consumer query it. No custom jOOQ binding is necessary.

**Alternatives considered**: a `text` column — no validation. A hand-built JSON string in the
domain — business code would own a storage format.

## D3 — Events are domain records, collected by the aggregate

**Decision**: add the package `com.minidoodle.calendar.domain.event` with a sealed interface
`CalendarEvent` and 5 records: `SlotCreated`, `SlotUpdated`, `SlotDeleted`, `MeetingBooked`,
`MeetingCancelled`. Each record has `eventId`, `occurredAt`, `calendarId`, and `aggregateId`.
`TimeSlot` keeps a list of events. `book`, `cancelMeeting`, `changeTime`, `changeStatus`, and
`recordDeletion` add an event only when the state changes. `drainEvents()` returns the list and
clears it.

**Rationale**: the aggregate knows whether a transition occurred. `changeStatus` already returns
early for an equal status. `changeTime` gets the same guard, so FR-003 holds in one place. The
domain uses `java..` only, so R1 and R2 stay green.

**Alternatives considered**: the service builds the events from the returned state — the no-op
rule would depend on each caller. The adapter emits events — a business fact would live in SQL
code.

## D4 — The application service owns every write transaction

**Decision**: add `@Transactional` to `SlotApplicationService.createSlot`, and remove the
`dsl.transaction(...)` call from `JooqSlotRepository.insert`. All 5 write paths then run in the
service transaction. `EventOutbox.append(...)` runs after the state write in the same transaction.

**Rationale**: FR-004 needs one unit. The repository-owned transaction existed only to let the
old retry loop re-run the transaction. ADR 0001 removed that loop, so the reason is gone.

**Alternatives considered**: pass the events into `SlotRepository.insert(...)` — the outbox would
become part of the persistence port. Open a `TransactionTemplate` in the service — the same result
with more code.

## D5 — The relay is one poller with a fixed delay and a bounded batch

**Decision**: `OutboxRelay` in `adapter.out.outbox` has
`@Scheduled(fixedDelayString = "${app.outbox.poll-interval:500ms}")`. It calls `pollOnce()`, which
selects at most 50 pending rows with `ORDER BY id FOR UPDATE SKIP LOCKED` in its own transaction.
`pollOnce()` is public, so a test calls it directly. Add `@EnableScheduling` to the application
class.

**Rationale**: a test must not wait for a clock. The batch bound protects the request path and the
database. `SKIP LOCKED` keeps a second instance from blocking on the same rows.

**Alternatives considered**: `@TransactionalEventListener(AFTER_COMMIT)` — loses an event when the
process stops. An external poller process — a second deployable for one table.

## D6 — A failure increments the attempt count, and 5 failures end the attempts

**Decision**: `pollOnce()` publishes one row at a time. On success it sets
`state='PUBLISHED'` and `published_at`. On failure it increments `attempts`, writes `last_error`,
and then sets `state='FAILED'` and `failed_at` when `attempts` reaches 5. The row stays in the
table.

**Rationale**: FR-012 and FR-013. A failed row is visible and does not stop the batch. A dead
letter by state needs no second table.

**Alternatives considered**: an infinite retry — one poison row would fill the log. A separate
dead-letter table — more schema for no gain at this scale.

## D7 — Ordering holds for one instance

**Decision**: the poller reads rows in `id` order and publishes them one at a time. The
specification limits the order guarantee to one application instance (FR-014, Assumptions). Two
instances can publish two events of one slot out of order because of `SKIP LOCKED`. The plan
records this limit and the future fix, which is a partition of the batch by `aggregate_id`.

**Rationale**: one instance is the current deployment. A partition scheme adds complexity without
a current need (constitution IV).

**Alternatives considered**: an advisory lock per aggregate — correct across instances, but it
serializes delivery by aggregate and needs a lock table. A single global lock — a bottleneck.

## D8 — The publisher is a port, and the log is its first adapter

**Decision**: add `EventPublisher` to `application.port.out` with one method,
`void publish(OutboxMessage message)`. `LoggingEventPublisher` writes one entry at the information
level with the envelope fields and the event identifier. It writes the payload at the debug level.
The logger name is `com.minidoodle.outbox`.

**Rationale**: the seam is the reason for the feature. The log is temporary, and a later adapter
replaces it without a change to the relay. The envelope holds no user content, so information
level is safe (FR-010). The payload has the meeting title, so it stays at debug.

**Alternatives considered**: log inside the relay — the next delivery target would edit the relay.
Log the payload at information level — user content would go to the default log.

## D9 — The pending count is a cached gauge

**Decision**: `MicrometerOutboxMetrics` registers `minidoodle.outbox.published` and
`minidoodle.outbox.failed` counters, and `minidoodle.outbox.pending` as a `Gauge` over an
`AtomicInteger`. The relay updates the gauge after each poll with a count query, and sets the first
value when it starts.

**Rationale**: FR-016. A true gauge would query the table on each scrape. The cached value is at
most one poll interval old, and the poll writes the value that it already knows.

**Alternatives considered**: a scrape-time query — database load from monitoring. No gauge — a
stuck outbox would be invisible.

## D10 — Cleanup removes published rows only

**Decision**: `OutboxRelay.cleanupOnce()` runs from
`@Scheduled(fixedDelayString = "${app.outbox.cleanup-interval:1h}")`. It deletes published rows
with `published_at < now() - interval '7 days'` in a bounded statement. It does not delete failed
rows.

**Rationale**: FR-017. A failed row is evidence for an operator. The partial index on
`published_at` keeps the delete cheap.

**Alternatives considered**: delete with the poll — an extra query on each poll. Time-based
partitioning — a larger schema change.

## D11 — Tests use the real database and drive the relay directly

**Decision**: the test set is:

| Test | Type | What it proves |
| --- | --- | --- |
| `TimeSlotEventsTest` | unit | one event per real transition, no event for an equal status or an equal time |
| `OutboxPayloadSerializationTest` | adapter unit | the 5 payload documents match the schema |
| `OutboxCaptureIT` | transactional IT | a booking writes one row in the same transaction; a failed booking writes none |
| `OutboxDeliveryIT` | non-transactional IT | `pollOnce()` publishes and marks, keeps order, counts attempts, sets failed, resumes from committed rows |
| `OutboxCleanupIT` | non-transactional IT | old published rows go, failed rows stay |
| `OutboxMetricsIT` | non-transactional IT | the 3 meters report the 3 counts |
| web IT | MockMvc | the 5 flows still answer as before; no endpoint is added |

`OutboxCaptureIT` extends `PostgresIntegrationTest` and relies on its rollback. The other ITs run
without `@Transactional` and clean their rows in `@AfterEach`, in the style of
`SlotRepositoryConcurrencyIT`.

**Rationale**: a transactional test cannot show a committed row to a relay in another transaction.
The specification asks for both properties, so the tests split them.

**Alternatives considered**: a test-only "publish in my transaction" switch — it would not prove
the real path.

## D12 — The existing mutation-event log lines move to the outbox

**Decision**: delete the `log.info("event=...")` lines from `MicrometerSlotMetrics` and
`MicrometerMeetingMetrics` for `slot.created`, `slot.updated`, `slot.deleted`, `meeting.booked`,
and `meeting.cancelled`. Keep the counters and timers. Keep the `meeting.viewed` and
`availability.query` lines, because they report read activity.

**Rationale**: FR-019. The old lines run before the commit and can describe a failed operation.
Two lines for one event would also drift. The payload now carries the facts that those lines had
(`status`, `start_at`, `duration_minutes`, `participant_count`).

**Alternatives considered**: keep both — duplicate events in the log. Remove the read lines too —
a loss of read telemetry with no requirement behind it.

## D13 — Build and documentation follow the existing gates

**Decision**: jOOQ code generation reads the live database after Flyway, so the new migration
creates the generated `OutboxEvents` table class on the next `./gradlew build`. No build change is
necessary. Document the feature in `docs/ARCHITECTURE.md` and in a new ADR,
`docs/adr/0002-event-outbox.md`. Update the specification set with the plan artifacts. Write all
prose in STE.

**Rationale**: the build already gates on coverage, ArchUnit, Checkstyle, and Spotless. The
constitution requires documentation parity (principle V). The repository now keeps decisions in
`docs/adr/`.

**Alternatives considered**: a Hibernate-style manual DDL — forbidden by principle II. A separate
module — no boundary benefit (constitution IV).
