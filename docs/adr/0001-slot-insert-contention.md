# ADR 0001: Return 503 for slot insert contention

Status: Accepted.
Date: 2026-10-20

## Terms

- **Exclusion constraint**: a database rule that prevents overlapping row ranges in one calendar. The rule is `ex_slot_no_overlap`.
- **Transaction**: a group of database operations that commit or roll back as one unit.
- **Exclusion check**: the test that the database does when a transaction inserts or updates a
  range. The test reads the ranges of other rows.
- **Wait cycle**: a set of transactions in which each transaction waits for the next transaction.
- **Deadlock**: a wait cycle that cannot end without help.
- **Victim**: the transaction that the database aborts to break a deadlock.
- **SQLSTATE**: the five-character code that the database returns with an error.

## Context

The database prevents overlapping slots with an exclusion constraint on each slot range.
The constraint is correct. It can return two different errors.
The service must distinguish these errors.

### The wait cycle

Two clients insert overlapping slots in the same calendar.
Each transaction inserts only one slot.
PostgreSQL inserts each index entry before it scans for exclusion conflicts.
The concurrent operations can occur in this sequence:

1. Transaction 1 inserts slot A and its index entry before it starts its exclusion scan.
2. Transaction 2 inserts slot B and its index entry before it starts its exclusion scan. Slot B overlaps slot A.
3. Transaction 1 scans for exclusion conflicts. It finds the uncommitted entry for slot B and waits for transaction 2.
4. Transaction 2 scans for exclusion conflicts. It finds the uncommitted entry for slot A and waits for transaction 1.
5. The deadlock detector finds this wait cycle and aborts one transaction. That transaction receives SQLSTATE 40P01.

The exclusion scan can find uncommitted index entries.
It must wait for the other transaction before it can determine the final result.
This sequence uses one slot write in each transaction.
This ADR gives no measurement of its duration or frequency.

The database selects the victim. The selection is not related to the request of the client.
The other transaction continues.
A new attempt can succeed or find an overlap if the other transaction committed.

A wait cycle needs at least two writers. A single writer cannot cause a 40P01 error.
The database waits for the period specified by `deadlock_timeout` before it aborts a transaction.
The default value is 1 second.

### The single waiter

An overlap can occur without a wait cycle.
Only one transaction waits in this example.

1. Transaction 1 inserts slot A.
2. Transaction 2 inserts slot B. The range of slot B overlaps the range of slot A. Transaction 2
   waits for transaction 1.
3. Transaction 1 commits. Transaction 2 runs the exclusion check again. Transaction 2 now sees
   row A. Transaction 2 receives SQLSTATE 23P01.
4. Transaction 1 rolls back instead. Transaction 2 continues and commits.

Transaction 2 never sees a deadlock. It sees the final state.

### The two classes of error

- **23P01 exclusion_violation** is a business error. The slot overlaps a committed slot. A new
  attempt with the same body fails again. The client must select another time.
- **40P01 deadlock_detected** is not a business error. The request was valid. The database aborted its transaction because of contention with another transaction. A new attempt can succeed.

SQLSTATE 40001 serialization_failure has the same property as 40P01. The database cannot prove
that the exclusion check is consistent. This error can occur with REPEATABLE READ isolation, with
SERIALIZABLE isolation, or at COMMIT.

### What the code did

`SlotWriteRetry` ran the insert again for 40P01 and 40001. It waited 10 ms, then 20 ms, and so on.
The insert opened its own transaction. Each attempt thus started with a new transaction. The code
was correct. It hid the difference between the two error classes.

Two problems came from the retry.

- The server controlled the wait. The client did not control it. A wait of up to 150 ms per request is
  small. It is still a cost that the client pays without a choice.
- The retry loop ran the statement, not the transaction, on the update path. After a 40P01 error,
  PostgreSQL stops the whole transaction. Every later statement in that transaction receives
  SQLSTATE 25P02. The retry could not succeed on that path.

## Decision

1. The persistence adapter MUST convert SQLSTATE 23P01 to a slot overlap error. The web adapter
   MUST return 409 Conflict.
2. The persistence adapter MUST convert SQLSTATE 40P01 and 40001 to a slot contention error. The
   web adapter MUST return 503 Service Unavailable. The response MUST have the header `Retry-After`
   with the value 1.
3. The server MUST NOT run a write again in the same request. Do not add a retry loop in the
   persistence adapter, in the application service, or in the web adapter.
4. The insert MUST keep its own transaction. The slot and its meeting commit or roll back together.

### Why 503 and not 409

A 409 says that the request cannot succeed in the current state of the resource. A new attempt
with the same body fails again. This statement is true for 23P01.

40P01 is different. The request is valid. Only the timing is wrong. A 409 would tell the client
that the time slot is not available, and that is not true. A 503 says that the service cannot do
the operation now. The header `Retry-After` tells the client when to send the request again.

### Why not 500

A 500 says that the service has a defect. The client cannot correct a 500. The service resolves a
40P01 with a new attempt, so the client must know that a new attempt is safe.

## Consequences

Positive:

- The status code that the client receives is correct for each error class.
- The client waits only for deadlock detection. The client does not wait for a retry loop.
- The database closes the failed transaction at the first abort, so the next request can continue
  at once.
- The retry policy, sleep function, and their tests have been removed.
- Removal of the class also removed the retry that could not succeed on the update path.

Negative:

- The client MUST send the request again after a 503. This is a new part of the API contract.
- The load test in `loadtest/` records 503 responses separately from business refusals and unexpected failures.
  A zero counter does not prove that the 503 path operates correctly.
  On a fresh dataset, the normal workload avoids intentional slot overlap.
  External writers or stale data can cause conflicts.
  The recorded runs do not establish a deadlock frequency.
  Read the 503 counter together with the success counter.
- `SlotRepositoryConcurrencyIT` asserts that every request that loses a concurrency race receives a slot overlap error. The test
  MUST accept the slot contention error too.

## Implementation notes

- `SqlState` keeps the four constants and the cause-chain lookup. It is the only place that knows
  an SQLSTATE value.
- Replace `SlotWriteRetry` with a translator that does not loop. Apply it to `insert` and to
  `update`.
- Add `SlotContentionException` to the domain exceptions. It extends `DomainException` like
  `SlotOverlapException`.
- Add the 503 mapping to `GlobalExceptionHandler`, with the header `Retry-After`.
- Keep the transaction in `insert`. Remove the retry from `update`.
- Replace `SlotWriteRetryTest` with a test of the translation. Change
  `SlotRepositoryConcurrencyIT` so that a loser receives `SlotOverlapException` or
  `SlotContentionException`. Add a web test for the 503 response and its header.
- The coverage floors stay as they are. Do not lower a floor.

## Alternatives

- **Keep the retry.** The code is correct and already exists.
  Rejected because the client cannot plan its own retry.
  The retry loop also hides a 40P01 in the request latency.
- **Remove the loop and keep the error as a 500.** Rejected. The client cannot tell a defect from
  contention, and `SlotRepositoryConcurrencyIT` becomes flaky.
- **Map 40P01 to 409.** Rejected. The status code says that the request can never succeed with
  that body, and that is false.

## References

- `src/main/resources/db/migration/V1__init.sql`, constraint `ex_slot_no_overlap`
- `src/main/java/com/minidoodle/calendar/adapter/out/persistence/SlotWriteErrors.java`
- `src/main/java/com/minidoodle/calendar/adapter/out/persistence/SqlState.java`
- PostgreSQL, "Deadlocks": https://www.postgresql.org/docs/16/explicit-locking.html#LOCKING-DEADLOCKS
- PostgreSQL, "Exclusion Constraints":
  https://www.postgresql.org/docs/16/ddl-constraints.html#DDL-CONSTRAINTS-EXCLUSION
- PostgreSQL 16 source, exclusion checks and the single-insert wait cycle:
  https://github.com/postgres/postgres/blob/REL_16_STABLE/src/backend/executor/execIndexing.c#L28-L45
