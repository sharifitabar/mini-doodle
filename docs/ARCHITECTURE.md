# Architecture decisions

The calendar context uses ports and adapters. The domain and application ports depend only on
Java and core types. Application services retain Spring service and transaction annotations as
an explicit wiring compromise. Configuration and observability implementations live in outbound
adapters; the application depends on their ports. Web adapters invoke inbound ports and map
core results to HTTP DTOs.

## Slot and meeting consistency

TimeSlot and Meeting remain separate aggregate roots, referenced by identifiers. Meeting owns
its participant values and content validation. TimeSlot owns its time range and manual status.
The domain BookingPolicy owns the cross-aggregate booking decisions; application services load
locked facts and coordinate persistence: booking requires a free slot;
a booked slot cannot be edited or deleted; cancellation frees it.

These operations require immediate consistency. Update, delete, book and cancel acquire the same
slot row lock within a transaction before checking policy and writing. The locking repository port
requires every implementation to preserve that guarantee. Database exclusion, unique and foreign
key constraints remain the final integrity guards. Separate aggregates are a deliberate choice;
we do not introduce eventual consistency for this workflow. New write paths must use this protocol.

Metrics preserve their existing attempt/success instrumentation semantics; success counters are
recorded before transaction commit and are not an audit of committed database changes.
