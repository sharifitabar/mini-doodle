# Phase 1 Data Model: Availability Query

This feature is **read-only** and introduces **no new tables or migrations**. Availability is a
derived projection over the existing `time_slots` and `meetings` tables. The types below are
framework-free domain types plus the read used to populate them.

## Overview

```
TimeFrame (caller-provided [start, end))
     │
     ├── read: AvailabilityRepository.findOccupancies(calendarId, frame)
     │        └── List<SlotOccupancy>   (from time_slots LEFT JOIN meetings)
     │
     └── calculate: AvailabilityCalculator.calculate(frame, occupancies)
              └── Availability { TimeFrame, List<AvailabilityInterval> }
                        AvailabilityInterval = [start, end) + AvailabilityKind
```

## Domain Types

### TimeFrame

- **Represents**: The half-open window `[start, end)` the availability is computed over.
- **Attributes**: `OffsetDateTime start`, `OffsetDateTime end`.
- **Invariants** (owned by the type, FR-002/FR-013):
  - both bounds are required.
  - `end` is strictly after `start`.
  - The span is at most `MAX_SPAN` (**31 days**).
  - Both bounds are normalized to UTC and truncated to microseconds.
- **Errors**: violations raise `InvalidTimeFrameException` → `400`.
- **`intersects(range)` / `clip(range)`**: helpers to test overlap and clip a slot range to the
  frame at microsecond precision.

### SlotOccupancy

- **Represents**: One of the caller's slots that overlaps the frame, with the facts needed to
  classify it.
- **Attributes**: `TimeRange range`, `SlotStatus status`, `boolean holdsMeeting`.
- **Derived rule** (FR-003/FR-004):
  `isBusy() = (status == BUSY) || holdsMeeting`. Otherwise the slot is free.
- **Source**: `time_slots` joined to `meetings` (a slot holds a meeting iff a matching `meetings`
  row exists). `SlotStatus` and `TimeRange` are existing domain types.

### AvailabilityKind

- **Represents**: The label of an interval — `FREE`, `BUSY`, or `UNALLOCATED` (FR-007).

### AvailabilityInterval

- **Represents**: A labeled half-open interval `[start, end)` within the frame.
- **Attributes**: `OffsetDateTime start`, `OffsetDateTime end`, `AvailabilityKind kind`.
- **Invariants**: `end` strictly after `start`. Both bounds UTC, microsecond precision. The interval
  lies within the frame.

### Availability

- **Represents**: The query result.
- **Attributes**: `TimeFrame frame`, `List<AvailabilityInterval> intervals` (ordered).
- **Invariants** (owned by the calculator, FR-005/FR-006):
  - intervals are ordered chronologically and are pairwise disjoint.
  - Consecutive intervals have different kinds or a gap — no two adjacent intervals share a kind
    (coalescing).
  - The union of the intervals equals the frame exactly: the first starts at `frame.start`, the
    last ends at `frame.end`, and every interval's end equals the next interval's start — no holes,
    no overlaps.

## Aggregation Rules

`AvailabilityCalculator.calculate(frame, occupancies)`:

1. Map each occupancy to a labeled block: `BUSY` if `isBusy()`, else `FREE`. Clip to the frame and
   drop blocks that fall entirely outside it.
2. Sort blocks by start (ties broken by end).
3. Sweep and coalesce: extend the current interval while the next block has the same kind and starts
   at or before the current end. Otherwise begin a new interval.
4. Fill the gaps between emitted intervals (and before the first / after the last) with
   `UNALLOCATED` intervals spanning from the frame start or previous end to the next start or frame
   end.
5. A frame with no blocks yields a single `UNALLOCATED` interval covering the whole frame (FR-009).

Boundary semantics: two free slots that touch (`a.end == b.start`) merge into one free window. A
free slot that touches a busy slot does **not** merge across kinds (FR-006, US2 scenario 3).

## Persistence (read-only)

No schema change. The read is a single indexed query:

```sql
SELECT ts.start_at,
       ts.duration_minutes,
       ts.status,
       (m.id IS NOT NULL) AS holds_meeting
FROM time_slots ts
LEFT JOIN meetings m ON m.slot_id = ts.id
WHERE ts.calendar_id = :calendarId
  AND ts.slot_range && tstzrange(:from, :to, '[)')   -- uses ix_time_slots_range
ORDER BY ts.start_at;
```

- The GiST index `ix_time_slots_range` serves the overlap predicate. `ix_time_slots_calendar_start`
  serves the calendar/order access.
- `holds_meeting` is derived from the `uq_meetings_slot` one-to-one relationship.
- The query is read-only (FR-010). No locks, writes, or migrations are involved.

## Validation & Error Mapping

| Condition | Source | HTTP |
|-----------|--------|------|
| Missing `from` or `to` | web binding | 400 `validation` |
| Unparseable date-time | web binding | 400 `validation` |
| Malformed/missing `X-User-Id` | existing caller resolution | 400 |
| Unknown caller (no calendar) | `CallerCalendarResolver` | 403 |
| `end <= start`, or span > 31 days | `TimeFrame` / `InvalidTimeFrameException` | 400 `invalid-time-frame` |

## Traceability

| Requirement | Where satisfied |
|-------------|-----------------|
| FR-001 frame-based query | `AvailabilityQuery`, `AvailabilityController` |
| FR-002 bounded frame | `TimeFrame` invariants |
| FR-003 free intervals | `AvailabilityCalculator` (FREE blocks, coalesced) |
| FR-004 busy intervals | `SlotOccupancy.isBusy()`, `AvailabilityCalculator` |
| FR-005 unallocated | `AvailabilityCalculator` (gap fill) |
| FR-006 ordered disjoint full coverage | `Availability` invariants |
| FR-007 labels | `AvailabilityKind` |
| FR-008 own-calendar only | `CallerCalendarResolver`, scoped read query |
| FR-009 empty frame | `AvailabilityCalculator` empty-blocks case |
| FR-010 read-only | read-only repository query. No migration |
| FR-011 unknown caller | `CallerCalendarResolver` (403) |
| FR-012 clear errors | `GlobalExceptionHandler` (+`InvalidTimeFrameException`) |
| FR-013 UTC / half-open | `TimeFrame`, `TimeRange` microsecond truncation |
| FR-014 prompt reads | indexed range query. Bounded frame |
