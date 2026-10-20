# ADR 0001: Return 503 for slot insert contention

Status: Accepted.
Date: 2026-10-20

## Terms

- **Exclusion constraint**: a database rule that prevents two rows from having overlapping ranges
  in one calendar. The rule is `ex_slot_no_overlap`.
- **Transaction**: a group of database operations that commit or roll back as one unit.
- **Exclusion check**: the test that the database does when a transaction inserts or updates a
  range. The test reads the ranges of other rows.
- **Wait cycle**: a set of transactions in which each transaction waits for the next transaction.
- **Deadlock**: a wait cycle that cannot end without help.
- **Victim**: the transaction that the database aborts to break a deadlock.
- **SQLSTATE**: the five-character code that the database returns with an error.

## Context

The database prevents overlapping slots. The constraint is an exclusion constraint over the
range of each slot. The constraint is correct. The constraint can return two different errors,
and the service must keep them apart.

### The wait cycle

Two clients insert overlapping slots in the same calendar.

1. Transaction 1 starts. Transaction 1 inserts slot A. Transaction 1 holds a lock on the new row.
2. Transaction 2 starts. Transaction 2 inserts slot B. The range of slot B overlaps the range of
   slot A. Transaction 2 cannot see row A, because transaction 1 did not commit. The exclusion
   check of transaction 2 waits for transaction 1.
3. Transaction 1 inserts slot C. The range of slot C overlaps the range of slot B. Transaction 1
   cannot see row B. The exclusion check of transaction 1 waits for transaction 2.
4. Transaction 1 waits for transaction 2. Transaction 2 waits for transaction 1. This is a wait
   cycle.
5. The database deadlock detector finds the wait cycle. The detector aborts one transaction. The
   aborted transaction receives SQLSTATE 40P01.

The database selects the victim. The selection is not related to the request of the client. The
same request can succeed after a new attempt. The other transaction continues.

A wait cycle needs at least two writers. A single writer cannot cause a 40P01 error. The database
waits for the value of `deadlock_timeout` before it aborts a transaction. The default value is 1
second.

### The single waiter

Most overlaps do not make a wait cycle. Only one transaction waits in this case.

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
- **40P01 deadlock_detected** is not a business error. The request was valid. The client lost a
  race between two transactions. A new attempt can succeed.

SQLSTATE 40001 serialization_failure has the same property as 40P01. The database cannot prove
that the exclusion check is consistent. This error can occur with REPEATABLE READ isolation, with
SERIALIZABLE isolation, or at COMMIT.

### What the code did

`SlotWriteRetry` ran the insert again for 40P01 and 40001. It waited 10 ms, then 20 ms, and so on.
The insert opened its own transaction, so each attempt started with a clean transaction. The code
was correct. It hid the difference between the two error classes.

Two problems came from the retry.

- The request waited for the server, not for the client. A wait of up to 150 ms per request is
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
- The retry policy, the sleep function, and their tests are gone.
- The broken retry on the update path is gone with the class.

Negative:

- The client MUST send the request again after a 503. This is a new part of the API contract.
- The load test in `loadtest/` shows 503 responses when the contention is high. The result is a
  measurement, not a defect. Read the 503 counter together with the success counter.
- `SlotRepositoryConcurrencyIT` asserts that every loser receives a slot overlap error. The test
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

- **Keep the retry.** Correct, and the code exists. Rejected because the client cannot plan its
  own retry, and because a retry loop hides a 40P01 in the request latency.
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
