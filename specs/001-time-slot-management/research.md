# Phase 0 Research: Time Slot Management

All Technical Context unknowns are resolved below (no `NEEDS CLARIFICATION` remain).

## Decision: Domain model is framework-free (hexagonal core)

**Reason**: Constitution I. Business rules (overlap, duration bounds, status
transitions) must be testable without Spring or a database. `TimeSlot` and `TimeRange` are
plain Java. Application services depend on outbound ports (interfaces). JOOQ lives only in
the persistence adapter.

**Alternatives considered**: JPA entities as the domain model (rejected: leaks persistence
and lazy-loading semantics into the domain). Anemic model with rules in services
(rejected: violates the aggregate-owns-invariants rule).

## Decision: Persistence with jOOQ (not JPA/Hibernate)

**Reason**: Overlap checks and availability-style queries are SQL-first. jOOQ gives
explicit, compile-checked SQL and precise control over range predicates and exclusion
constraints without ORM impedance. Matches the locked stack.

**Alternatives considered**: Spring Data JPA (rejected: weaker fit for range/exclusion
work, more impedance). Plain JDBC (rejected: jOOQ provides type safety and mapping with
little overhead).

## Decision: Overlap prevention through PostgreSQL exclusion constraint

**Reason**: Constitution II — correctness first, and race-proof. With
`btree_gist` enabled, a constraint on the slot table:

```
EXCLUDE USING gist (calendar_id WITH =, slot_range WITH &&)
```

where `slot_range` is a `tstzrange` derived from start and duration. Two slots in the same
calendar may not overlap. Concurrent inserts are serialized by the index, and the second
insert fails deterministically. This satisfies FR-010 and SC-002 without application
locking.

**Alternatives considered**: Application-level `SELECT ... FOR UPDATE` then insert
(rejected: extra round trip and still relies on lock discipline). Optimistic versioning
(rejected: does not model interval overlap cleanly). Serializable isolation (rejected:
broader effect on other operations, retry complexity).

## Decision: Range column is generated, source of truth is start + duration

**Reason**: The API speaks in `startAt` (offset date-time) + `durationMinutes`
(FR-001, FR-002). Store `start_at timestamptz` and `duration_minutes int`, plus a
generated column `slot_range tstzrange GENERATED ALWAYS AS
(tstzrange(start_at, start_at + make_interval(mins => duration_minutes), '[)')) STORED`.
The generated column keeps data normalized and lets the constraint and range queries use an
index.

**Alternatives considered**: Store only the tstzrange (rejected: loses explicit
duration semantics and complicates updates). Store explicit end time (viable, but duration
is the user-facing input — generated range avoids drift).

## Decision: Timestamps in UTC through OffsetDateTime ↔ timestamptz

**Reason**: FR-014. Client sends/ receives ISO-8601 with offset. Persist as
`timestamptz` (stored in UTC). jOOQ maps `OffsetDateTime` to `timestamptz`. Display offset
is preserved on the wire. Storage is unambiguous.

**Alternatives considered**: Store zone id per slot (rejected: not required, offsets
are sufficient for this feature). Local time (rejected: ambiguous across DST).

## Decision: Demo authentication through `X-User-Id` header

**Reason**: Constitution "Technology Stack" and spec assumptions — account management
is out of scope. A small web filter resolves the caller into a `UserId`. Unknown ids are
rejected (403) or provisioned automatically minimally (see identity below). Enables ownership rules
(FR-008, FR-009) to be enforced now and swapped for real auth later behind the same port.

**Alternatives considered**: Full JWT/login (rejected: out of scope, violates IV). No
identity (rejected: makes ownership/FR-009 impossible to test).

## Decision: Identity and calendar provisioning

**Reason**: Each user has exactly one calendar (assumption). Keep a `users` table and a
`calendars` table with a unique `user_id`. The `X-User-Id` value is resolved against
`users`. A request for an unknown user is rejected with 404/403 rather than being
created automatically, keeping the demo predictable. Seed users for local runs.

**Alternatives considered**: Auto-create user+calendar on first request (rejected:
surprising writes on read paths, harder tests). Skip tables and key slots by raw user-id
(rejected: weaker integrity, no place for future user features).

## Decision: Slot identity and status

**Reason**: `SlotId` is a UUID (server-generated) — safe to expose, no coordination.
`SlotStatus` is an enum `FREE|BUSY`, defaulting to `FREE` (FR-003). Status changes are
idempotent (setting the current status succeeds, FR/US2).

**Alternatives considered**: Auto-increment ids (rejected: enumerable, leaks volume).
String status (rejected: weaker typing).

## Decision: Error model uses RFC 7807 Problem Details

**Reason**: FR-015 requires distinguishing validation, authorization, and not-found
failures. Spring MVC maps domain exceptions to `application/problem+json` with stable
`type` values and HTTP codes: 400 (validation), 403 (forbidden), 404 (not found), 409
(overlap / state conflict).

**Alternatives considered**: Ad-hoc error JSON (rejected: inconsistent, harder to
document). 422 For validation (rejected: 400 is the least-surprising default).

## Decision: Listing is paginated with a bounded page size

**Reason**: FR-007 plus SC-004 scale. `GET /slots` supports `from`, `to`, `status`,
`limit`, `offset` with a server-enforced max `limit` (for example, 100). Range filtering uses the
`slot_range` index. Bounded responses keep p99 latency independent of total data volume.

**Alternatives considered**: Return all slots (rejected: unbounded memory/latency risk).
Cursor pagination (rejected for now: offset is sufficient at this scope, revisit if needed).

## Decision: Testing strategy

**Reason**: Constitution III. Three layers:
1. Pure domain unit tests for overlap/duration/status rules.
2. Integration tests against a real PostgreSQL through Testcontainers that assert the
   exclusion constraint rejects overlapping inserts and concurrent creation.
3. MockMvc contract tests per endpoint incl. error codes and ownership (403/404).

**Alternatives considered**: H2/embedded (rejected: cannot prove the PostgreSQL exclusion
constraint). Mocked repository-only tests (rejected: would miss the core correctness claim).

## Decision: Schema migration tooling

**Reason**: Constitution II. A single Flyway migration `V1__init.sql` creates the
`btree_gist` extension, `users`, `calendars`, `time_slots`, the generated `slot_range`
column, the exclusion constraint, and supporting indexes. jOOQ codegen runs against the
migrated schema.

**Alternatives considered**: Liquibase (rejected: locked stack is Flyway). Hibernate
ddl-auto (forbidden by constitution).
