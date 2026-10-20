# Phase 0 Research: Availability Query

Resolved decisions for the availability query feature. Each item records the decision, the
reason, and the alternatives that were considered and rejected.

## 1. Meaning of "aggregated"

**Decision**: The result is a single, chronologically ordered list of merged, half-open intervals
covering the requested frame, each labeled `FREE`, `BUSY`, or `UNALLOCATED`. Free slots are
coalesced into contiguous free windows. Busy time is the union of slots explicitly marked `BUSY`
and slots that hold a meeting. The remaining time inside the frame is `UNALLOCATED`.

**Reason**: "Free/busy availability" is the conventional meaning of an availability query (the
shape used by calendar free/busy interchange, for example RFC 5545). Merging contiguous free time answers
"When am I free between X and Y?" in one call, which is the stated goal. Treating time covered by
no slot as a third, distinct label preserves the honest distinction between "declared free" and
"nothing declared" — the slot model does not treat unscheduled time as available.

**Alternatives considered**:
- *Duration summary* (total free/busy minutes per frame): loses the locations of free windows, so
  it cannot answer "when" — the core question.
- *Time-bucketed report* (free/busy per day or hour): introduces a bucketing policy and a coarser
  answer than the underlying data. Useful for dashboards, not for this query.
- *Two parallel arrays* (`free[]`, `busy[]`): makes the "covers the frame exactly" guarantee and
  the free/busy/unallocated ordering implicit. A single labeled, ordered list is simpler to consume
  and to verify.

## 2. Where availability is computed

**Decision**: Compute the aggregation with a framework-free domain calculator
(`domain/service/AvailabilityCalculator`) over domain value types, driven by an application service.

**Reason**: Constitution I requires business rules to live in the domain, free of framework and
I/O dependencies. Merging, clipping, and classification are pure functions of the slots and the
frame and are exhaustively unit-testable without Spring or PostgreSQL.

**Alternatives considered**:
- *Compute entirely in SQL* (range aggregation in PostgreSQL): powerful but harder to unit test,
  ties business semantics to the database, and duplicates the question of "what is free" in SQL.
  The database remains the source of truth for data (constitution II). The *interpretation* stays
  in the domain.
- *Inline in the application service*: works, but scatters testable logic into a layer whose job is
  orchestration.

## 3. Reading slot occupancy

**Decision**: Add one outbound read port, `AvailabilityRepository`, with
`findOccupancies(calendarId, frame)` returning `SlotOccupancy` values (range, status,
`holdsMeeting`). It is implemented by a single `LEFT JOIN` of `time_slots` to `meetings`, filtered
by `calendar_id` and `slot_range && tstzrange(frame)`, ordered by `start_at`.

**Reason**: Deciding whether a slot is busy needs both the slot's status and whether it holds a
meeting. A single range join returns everything in one round trip and lets the existing
`ix_time_slots_range` (GiST) and `ix_time_slots_calendar_start` indexes serve the predicate.

**Alternatives considered**:
- *Reuse `SlotRepository.find` + `MeetingRepository.existsBySlotId` per slot*: an N+1 query pattern.
  Acceptable for a page of ≤100 slots, not for an unbounded frame that may cover thousands of slots.
- *Add the join method to `SlotRepository`*: mixes a read-model projection into the aggregate
  repository. A dedicated read port keeps the aggregate repository focused on the `TimeSlot` write
  model.

## 4. Maximum frame span

**Decision**: A frame must be bounded and at most **31 days** long (a domain constant on
`TimeFrame`). Frames that are missing a bound, inverted (`end <= start`), or longer than the maximum
are rejected with a validation error (400).

**Reason**: An unbounded query would defeat the "bounded queries" design (constitution I/IV) and
the p99 target (SC-001). A calendar-month window is a natural, generous default for scheduling.
The value is a single constant, easy to change. Durations are already bounded per-slot (1440 min).

**Alternatives considered**:
- *Unbounded frames*: unbounded result sizes and unpredictable latency — rejected.
- *Pagination of intervals*: intervals are a derived, already-bounded set. Pagination adds
  complexity without value (YAGNI).
- *Spring property / env var*: plausible, but premature — nothing needs to tune it yet. A constant
  keeps the domain framework-free.

## 5. Endpoint and addressing

**Decision**: `GET /api/v1/availability?from={from}&to={to}` with the required `X-User-Id` header.
`from`/`to` are ISO-8601 date-times with an explicit offset. Both are required. The response echoes
the frame and lists the intervals.

**Reason**: Availability is a read-only, caller-scoped resource. A `GET` with query parameters
matches the existing list endpoint (`GET /api/v1/slots?from=&to=`) and the `X-User-Id` demo-auth
convention. Keeping it a top-level `/availability` path (rather than under a slot) reflects that the
result is about the caller's calendar, not one slot.

**Alternatives considered**:
- *`POST` with a body*: a query is not a state change. `GET` is cacheable and idiomatic.
- *Under `/slots/availability`*: implies a per-slot resource. The result spans many slots.

## 6. Precision and time zone

**Decision**: Frame bounds and all interval boundaries are normalized to UTC and truncated to
microsecond precision, exactly as `TimeRange` does for slots. Interval bounds are half-open
(`[start, end)`): a boundary belongs to the interval that starts at it.

**Reason**: PostgreSQL `timestamptz` stores microseconds. The existing domain already truncates
to microseconds to make equality and persistence round-trip exactly. Matching that here keeps
availability boundaries identical to the slot boundaries they are derived from and avoids the
local-pass / CI-fail class of bug seen previously.

**Alternatives considered**: *Keep caller offsets in the response* — rejected. The service already
normalizes everything to UTC for storage and comparison, and clients can convert for display.

## 7. Error model

**Decision**: Reuse the RFC 9457 problem-details model. A new `InvalidTimeFrameException` maps to
`400` for missing, inverted, or oversized frames. A missing query parameter also maps to `400`.
Unknown callers keep returning `403` and malformed caller ids `400`.

**Reason**: Constitution V and the existing features already define a consistent error contract.
Availability introduces only one new failure kind (the frame), so one new exception and one new
handler are sufficient.

**Alternatives considered**: *`422` for the frame* — the project already uses `400` for validation
failures (see `SlotController`), so `400` stays consistent.

## 8. Ownership isolation

**Decision**: Availability is always computed for the caller's own calendar, resolved through the
existing `CallerCalendarResolver`. There is no way to request another user's availability.

**Reason**: FR-008 and the existing ownership-isolation guarantee (unknown or foreign resources
are never disclosed). Cross-user/team availability is explicitly out of scope.
