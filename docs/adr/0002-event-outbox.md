# ADR 0002: A transactional outbox for calendar events

Status: Accepted.
Date: 2026-10-21

## Context

Five flows change the calendar data: time slot creation, time slot update, time slot deletion,
meeting booking, and meeting cancellation.

The metrics adapters wrote one log line for each change.
The line was synchronous, and it could describe an operation that later failed.
A consumer cannot use the line.
The line is lost when the process stops, and no record exists for a replay.

The service needs one record for each change.
The record must exist only when the state change succeeds.
The record must survive a restart.
The first delivery target is the application log.

## Decision

1. The aggregate records one domain event for each real transition.
2. The application service writes the events to the `outbox_events` table in the same transaction as
   the state change.
3. A scheduled relay reads the pending rows in capture order with `FOR UPDATE SKIP LOCKED`.
4. The relay sends each row through the `EventPublisher` port.
   The first adapter writes the application log.
   The envelope goes to the information level.
   The changed facts go to the debug level.
5. A failed delivery increases the attempt count.
   After 5 attempts, the row becomes failed, and the relay stops.
6. Published rows stay for 7 days.
   The cleanup action removes them.
   Failed rows stay.

## Why an event listener after the commit is not sufficient

A `@TransactionalEventListener(AFTER_COMMIT)` listener is smaller.
It loses the event when the process stops between the commit and the listener.
It also has no record for a replay.
The specification requires a record that survives a restart.

## Why the log is the first delivery target

The feature must show a visible output before a consumer exists.
The log needs no new component and no new dependency.
The `EventPublisher` port keeps the next target, for example a message broker, at a cost of one
adapter.

## Consequences

Positive:

- The record and the state change commit or roll back together.
- A restart sends the pending events.
- One table shows the delivery state, the attempt count, and the last error.
- Three meters show the state of the outbox.

Negative:

- The feature adds a table, a relay, and a retention rule.
- Delivery is at-least-once.
  A consumer can receive one event two times.
- The order guarantee holds for one application instance.
  Two instances can deliver two events of one slot out of order.
  A partition by `aggregate_id` is the future fix.

## Alternatives

- **An event listener after the commit.**
  Rejected: it loses an event on a crash and it cannot replay.
- **A synchronous log line.**
  Rejected: it is not a record, and it can report a failed operation.
- **A message broker now.**
  Rejected: no consumer needs it.
  It adds a component and a deployment.
- **A second table for the delivery state.**
  Rejected: one row and one state column are sufficient.

## References

- Specification: `specs/008-domain-event-outbox/spec.md`
- Research: `specs/008-domain-event-outbox/research.md`
- Delivery contract: `specs/008-domain-event-outbox/contracts/delivery-log.md`
- Migration: `src/main/resources/db/migration/V4__outbox_events.sql`
- Implementation: `src/main/java/com/minidoodle/calendar/adapter/out/outbox/`
