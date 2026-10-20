# Architecture decisions

The calendar context uses ports and adapters.
The domain and application ports depend only on Java and core types.
Application services keep Spring service and transaction annotations as an explicit configuration compromise.
Outbound adapters implement configuration and observability. The application depends on their ports.
Web adapters call inbound ports and map core results to HTTP data transfer objects (DTOs).

## Slot and meeting consistency

`TimeSlot` is the aggregate root. It owns its optional `Meeting` entity and the meeting's participants.
`Meeting` validates its content.
`TimeSlot` controls booking, cancellation, manual status and time changes, and permission to delete a slot.
Booking requires a free slot with no meeting.
A booked slot cannot be changed or deleted.
Cancellation removes the meeting and returns the slot to `FREE`.

`TimeSlot` methods enforce these rules.
There is no separate booking policy or check in an application service.
Application services resolve ownership and load the root.
They call domain methods and save through `SlotRepository`.
Meeting persistence is an internal helper of the outbound persistence adapter.
It is not a separate application port.

Existing tables and database constraints stay unchanged.
An insert or update saves the root and child in one transaction.
Cancellation cascades the deletion to participants.
If meeting identities are unchanged, existing participant rows are preserved.
Reads restore the complete aggregate.
Lists load children in batches to prevent N+1 queries.

Update, delete, book, and cancel operations acquire the same slot row lock before they load its meeting.
They hold the lock until the transaction ends.
This prevents two requests from using an old aggregate state.
Ordinary aggregate reads use a repeatable-read transaction for a consistent snapshot of the root and child.
New write paths must preserve this lock protocol.

An insert has no existing row to lock.
The exclusion constraint checks the new range instead.
The check waits while another transaction holds an uncommitted range.
Two transactions can wait for each other.
The database then aborts one transaction with SQLSTATE 40P01.

The request was valid. This error is not a business error.
The write returns 503 Service Unavailable with `Retry-After`.
The client sends the request again.
See [ADR 0001](adr/0001-slot-insert-contention.md) for the contract.

Metrics keep their existing meanings for attempts and successes.
The service records success counters before transaction commit.
These counters are not an audit of committed database changes.
