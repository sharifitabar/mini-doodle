# Architecture decisions

The calendar context uses ports and adapters. The domain and application ports depend only on
Java and core types. Application services retain Spring service and transaction annotations as
an explicit wiring compromise. Configuration and observability implementations live in outbound
adapters; the application depends on their ports. Web adapters invoke inbound ports and map
core results to HTTP DTOs.

## Slot and meeting consistency

TimeSlot is the aggregate root and owns its optional Meeting entity, including participants.
Meeting validates its content; TimeSlot controls booking, cancellation, manual status/time changes,
and deletion eligibility. Booking requires a free, unbooked slot. A booked slot cannot be edited
or deleted. Cancellation removes the meeting and returns the slot to FREE. These rules live in
TimeSlot methods, not a separate booking policy or application-service checks.

Application services resolve ownership, load the root, invoke domain behavior and save through
SlotRepository. Meeting persistence is an internal helper of the outbound persistence adapter,
not a separate application port. Existing tables and database constraints remain unchanged.
Aggregate insertion/update persists both the root and child in one transaction; cancellation
cascades participant deletion. Unchanged meeting identities preserve existing participant rows.
Reads restore the complete aggregate; lists batch child loading to avoid N+1 queries.

Update, delete, book and cancel acquire the same slot row lock before loading its meeting and
hold it until transaction completion. This prevents two requests from acting on stale aggregate
state. Ordinary aggregate reads use a repeatable-read transaction for a consistent root/child
snapshot. New write paths must preserve this locking protocol.

Metrics preserve their existing attempt/success instrumentation semantics; success counters are
recorded before transaction commit and are not an audit of committed database changes.
