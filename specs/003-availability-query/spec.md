# Feature Specification: Availability Query

**Feature Branch**: `003-availability-query`

**Created**: 2026-10-16

**Status**: Draft

**Input**: User description: "Availability query: return a user's free/busy availability aggregated over a requested time frame."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View your free time over a period (Priority: P1)

A user asks for their availability over a requested time frame, defined by a start and an end. The
system replies with the periods within that frame during which the user is free, merging the time
they have declared available into contiguous free windows.

**Why this priority**: This is the core purpose of the feature — a single request that answers
"when am I free between these two times?". Without it, a user can only inspect slots one by one.

**Independent Test**: Create several free slots over a frame — some contiguous, some separated by
busy time — request availability for the frame, and confirm the returned free windows exactly match
the user's merged free time clipped to the frame.

**Acceptance Scenarios**:

1. **Given** a user with free slots inside a frame, **When** they request availability for that
   frame, **Then** the response lists the user's free windows in chronological order, clipped to the
   frame.
2. **Given** two free slots that touch or overlap in time, **When** availability is requested,
   **Then** they appear as a single merged free window.
3. **Given** a free slot that starts before or ends after the requested frame, **When** availability
   is requested, **Then** the free window is clipped to the frame's boundaries.
4. **Given** a frame in which the user has no slots, **When** availability is requested, **Then**
   the response contains no free windows.

---

### User Story 2 - See busy time, including meetings (Priority: P2)

A user requests availability and also receives the busy periods within the frame — time covered by
slots they explicitly marked busy, and time occupied by scheduled meetings.

**Why this priority**: A scheduler needs the whole free/busy picture, not just free time. Separating
"declared busy" from "free" is what makes the result trustworthy, and it must reflect meetings.

**Independent Test**: With a frame containing an explicitly busy slot and a slot that holds a
meeting, request availability and confirm both are reported as busy windows.

**Acceptance Scenarios**:

1. **Given** a slot explicitly marked busy inside the frame, **When** availability is requested,
   **Then** that period is reported as a busy interval.
2. **Given** a slot that holds a meeting, **When** availability is requested, **Then** that period
   is reported as busy, not free.
3. **Given** a free slot immediately followed by a busy slot, **When** availability is requested,
   **Then** they are reported as two separate intervals (free and busy) and are not merged.

---

### User Story 3 - See unallocated time in the frame (Priority: P3)

A user requests availability and receives the portions of the frame that are covered by no slot at
all, so they can see where they have not declared any availability.

**Why this priority**: Completes the picture — free, busy, and neither — and helps users spot gaps
in their declared schedule. Useful but not required for the core free-time query.

**Independent Test**: Request availability for a frame that is only partly covered by slots, and
confirm the uncovered portions are returned as unallocated gaps.

**Acceptance Scenarios**:

1. **Given** a frame only partly covered by slots, **When** availability is requested, **Then** the
   uncovered portions are reported as unallocated intervals.
2. **Given** a frame fully covered by free and busy slots with no holes, **When** availability is
   requested, **Then** the response reports no unallocated intervals.
3. **Given** a frame that only partially overlaps the user's slots, **When** availability is
   requested, **Then** only the portions inside the frame are considered.

---

### Edge Cases

- A frame whose end is not later than its start (invalid).
- A frame missing a start or an end (invalid).
- A frame larger than the allowed maximum span (rejected).
- A frame entirely before any of the user's slots, or entirely inside a single slot.
- Slots overlapping the frame's start and/or end (clip to the frame).
- Slots that touch exactly at their boundaries — merged when the same kind, kept separate when free
  meets busy.
- A slot that holds a meeting (counts as busy, never as free).
- A caller with no slots at all.
- A missing or unknown caller (authentication error), and never disclosing another user's
  availability.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to request their aggregated availability for a time frame defined
  by a start and an end.
- **FR-002**: The system MUST require both a start and an end, MUST reject a frame whose end is not
  later than its start, and MUST reject a frame whose span exceeds the allowed maximum.
- **FR-003**: The system MUST compute FREE intervals as the union of the caller's free slots within
  the frame, excluding any slot that holds a meeting, clipped to the frame, with contiguous or
  overlapping free intervals coalesced into single windows.
- **FR-004**: The system MUST compute BUSY intervals as the union of the caller's slots explicitly
  marked busy and any of the caller's slots that hold a meeting, clipped to the frame and coalesced
  the same way.
- **FR-005**: The system MUST report UNALLOCATED intervals as the portions of the frame covered by
  neither a free nor a busy interval.
- **FR-006**: The response MUST present intervals in chronological order as a set of disjoint
  intervals that together cover the frame exactly, with no overlaps and no holes.
- **FR-007**: Each returned interval MUST be labeled as free, busy, or unallocated.
- **FR-008**: The system MUST restrict results to the caller's own calendar and MUST NOT disclose
  another user's availability.
- **FR-009**: A frame with no slots MUST yield no free and no busy intervals and a single
  unallocated interval spanning the frame.
- **FR-010**: The query MUST be read-only and MUST NOT create, modify, delete, or change the status
  of any slot, meeting, or other data.
- **FR-011**: The system MUST reject requests from a missing or unknown caller.
- **FR-012**: When a request is rejected, the system MUST return a clear, actionable error that
  distinguishes validation failures from authentication failures.
- **FR-013**: The system MUST handle times in UTC, consistently with existing slot and meeting data,
  and MUST interpret the requested frame boundaries inclusively at the start and exclusively at the
  end.
- **FR-014**: The system MUST return availability promptly for frames up to the allowed maximum span,
  independent of how many slots or meetings the user has.

### Key Entities *(include if feature involves data)*

- **Availability (computed view)**: The result of a query — the set of free, busy, and unallocated
  intervals covering the requested frame. It is derived on demand and is not stored.
- **Interval**: A labeled, half-open `[start, end)` period within the frame, classified as free,
  busy, or unallocated.
- **Time Frame**: The caller-provided `[start, end)` window the availability is computed over; it
  must be bounded and within the allowed maximum span.
- **Time Slot** (from time slot management): A user's declared block of time, either free or busy,
  which may hold a meeting.
- **Meeting** (from meeting scheduling): Occupies a slot and causes that slot to count as busy.
- **Calendar / User** (from time slot management): The owner of the slots whose availability is
  queried; the caller in the API contract.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A client can obtain aggregated availability in a single interaction with p99 latency
  ≤ 300 ms under the expected load.
- **SC-002**: For any valid frame, the returned free, busy, and unallocated intervals are disjoint
  and together cover the frame exactly — no overlaps and no gaps — 100% of the time.
- **SC-003**: Contiguous free time is always presented as a single merged window, never as multiple
  adjacent windows.
- **SC-004**: Immediately after a meeting is booked on a slot, every subsequent availability read
  reflects that slot as busy.
- **SC-005**: The system supports hundreds of users each holding thousands of slots, and availability
  reads stay within p99 ≤ 300 ms regardless of total data volume.
- **SC-006**: Valid availability requests complete without server errors (5xx rate below 0.1%), and
  every rejected request returns a clear, actionable reason.

## Assumptions

- "Aggregated" means coalescing the user's declared time into merged free and busy windows over the
  frame (a free/busy interval view); bucketed or duration-summary/statistics reports are out of
  scope.
- Availability is derived from existing slots and meetings; this feature introduces no new persisted
  data and does not alter slots or meetings.
- Time not covered by any slot is neither free nor busy and is reported as unallocated.
- Only the caller's own availability is queryable; availability across users (per-user comparison,
  team availability, or "find a time we are all free") is out of scope.
- The requested frame must be bounded and within a maximum span, chosen to keep responses
  predictable.
- Users, calendars, slots, meetings, ownership isolation, UTC storage, and free/busy slot semantics
  are defined by the time slot management and meeting scheduling features and are reused unchanged.
- Authentication and identity are handled elsewhere; the caller is identified as in the existing
  features.
- Notifications, calendar exports (e.g., ICS), recurring availability, and time-zone conversion
  beyond UTC are out of scope.
