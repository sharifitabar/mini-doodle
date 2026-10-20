# Feature Specification: Time Slot Management

**Feature Branch**: `001-time-slot-management`

**Created**: 2026-10-15

**Status**: Complete

**Input**: User description: "Time slot management: users create available time slots with a configurable duration in their personal calendar, modify and delete existing slots, and mark them busy or free."

## Clarifications

### Session 2026-10-15

- Q: SC-001 ("create a slot in under 10 seconds from opening the calendar") is not measurable for a backend-only service with no calendar UI — how should it be defined? → A: Reframe as service-observable: a single client interaction creates the slot and completes without noticeable delay (target p99 ≤ 200 ms under the expected load).
- Q: SC-004 ("remains responsive ... without noticeable delay") is unquantified — how should it be defined? → A: Quantify as service-observable: listing a user's slots completes with p99 ≤ 300 ms regardless of total data volume.
- Q: SC-005 ("90% of slot operations succeed on the user's first attempt") is not measurable for a backend with no UI — how should it be defined? → A: Reframe as server-observable: valid operations succeed without server errors (5xx rate < 0.1%), and every rejected operation returns a clear reason.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create an available time slot (Priority: P1)

A user opens their personal calendar.
They enter a start date-time and duration to define an available period.
The system adds the new slot to the calendar with an available status.

**Why this priority**: Users must declare availability before they can schedule meetings or query availability.
This capability is the basis of the platform.

**Independent Test**: Create a slot with a start time and duration.
Retrieve the user's calendar.
Make sure that it contains the slot with status "free".
This lets the user state when they are available.

**Acceptance Scenarios**:

1. **Given** a user with an empty calendar, **When** they create a slot starting at a
   future date-time with a valid duration, **Then** the slot is stored and returned as
   "free".
2. **Given** a user with an existing slot, **When** they create a second slot that does
   not overlap the first, **Then** both slots exist in the calendar.
3. **Given** a user with an existing slot, **When** they attempt to create a slot that
   overlaps an existing slot, **Then** the request is rejected and no slot is created.

---

### User Story 2 - Mark a slot busy or free (Priority: P2)

A user changes the availability status of an existing slot between "free" and "busy"
to reflect whether they can be booked during that time.

**Why this priority**: Slots let users change their availability status.
This is the second most valuable behavior after slot creation.
It is small and independent.

**Independent Test**: Mark an existing free slot busy.
Retrieve it and make sure that its status is "busy".
Mark it free again. Confirm the change.

**Acceptance Scenarios**:

1. **Given** an existing free slot, **When** the user marks it busy, **Then** later
   reads report the slot as "busy".
2. **Given** an existing busy slot, **When** the user marks it free, **Then** later
   reads report the slot as "free".
3. **Given** an existing slot, **When** the user sets it to the status it already has,
   **Then** the request succeeds and the status is unchanged.

---

### User Story 3 - Modify an existing time slot (Priority: P3)

A user adjusts a slot's start time and/or duration to correct or reschedule their
availability, without deleting and recreating it.

**Why this priority**: Real availability changes often. Editing is valuable but not
required for the MVP, so it ranks below creation and status changes.

**Independent Test**: Change the duration of an existing slot and make sure that the stored
slot reflects the new duration and end time.

**Acceptance Scenarios**:

1. **Given** an existing slot, **When** the user changes its duration, **Then** the slot
   is updated and its end time reflects the new duration.
2. **Given** an existing slot, **When** the user changes its start time to a free period,
   **Then** the slot is updated accordingly.
3. **Given** an existing slot, **When** the user changes it so that it would overlap
   another slot, **Then** the request is rejected and the slot is unchanged.

---

### User Story 4 - Delete a time slot (Priority: P4)

A user removes a slot that is no longer relevant from their calendar.

**Why this priority**: Deletion removes slots that are no longer needed.
The platform remains usable without it, so it has the lowest priority in this feature.

**Independent Test**: Create a slot, delete it, and make sure that it no longer appears in the
user's calendar.

**Acceptance Scenarios**:

1. **Given** an existing slot, **When** the user deletes it, **Then** it no longer
   appears in the calendar.
2. **Given** a slot that does not exist or belongs to another user, **When** the user
   attempts to delete it, **Then** the request is rejected and no data changes.

---

### Edge Cases

- Creating a slot with a start time in the past.
- Creating a slot with zero or negative duration.
- Creating or modifying a slot that overlaps an existing slot in the same calendar.
- Concurrent requests that each create a non-overlapping slot individually but overlap
  each other collectively.
- Operating on a slot that belongs to a different user.
- Operating on a slot that does not exist.
- A duration that exceeds the maximum allowed single-slot length.
- A slot that spans a daylight-saving time boundary or changes UTC offset.
- Changing only the duration such that the new end time overlaps a later slot.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to create a time slot in their personal calendar by
  providing a start date-time and a duration.
- **FR-002**: The duration MUST be configurable by the user and expressed in minutes.
- **FR-003**: Every newly created slot MUST default to the "free" status.
- **FR-004**: Users MUST be able to set a slot's status to "free" or "busy".
- **FR-005**: Users MUST be able to change an existing slot's start time and/or duration.
- **FR-006**: Users MUST be able to delete an existing slot.
- **FR-007**: Users MUST be able to retrieve a single slot and list the slots in their calendar.
  List results MUST use pagination with a bounded page size.
- **FR-008**: A slot MUST belong to exactly one user's calendar.
- **FR-009**: Users MUST NOT be able to view, modify, or delete slots that belong to
  another user.
- **FR-010**: A calendar MUST NOT contain two slots that overlap in time.
  The system MUST reject create or modify operations that introduce an overlap.
  This rule also applies to concurrent operations.
- **FR-011**: The system MUST reject a slot whose duration is not positive or that
  exceeds a configured maximum single-slot length.
- **FR-012**: The system MUST reject a slot whose start time is in the past.
- **FR-013**: All slot data MUST be persisted so it survives application restarts.
- **FR-014**: All date-times MUST be accepted and returned with explicit timezone offset
  information and stored unambiguously (in UTC).
- **FR-015**: When an operation is rejected, the system MUST return a clear, actionable
  error that distinguishes validation failures (for example, invalid duration, overlap) from
  authorization failures and not-found cases.
- **FR-016**: The system MUST reject any request whose caller identity is missing or does
  not resolve to a known user (authorization failure), and MUST NOT treat an unknown
  caller as the owner of any calendar.

### Key Entities *(include if feature involves data)*

- **Calendar**: A user's personal container for their time. Owns the slots belonging to
  that user. Each user is assumed to have exactly one calendar in this feature.
- **Time Slot**: A continuous block of time within a calendar, defined by a start
  date-time and a duration (from which an end date-time is derived), plus an availability
  status of "free" or "busy". Belongs to exactly one calendar. The system additionally
  records creation and last-update timestamps for auditability.
- **User**: The owner of a calendar. In this feature a user is an existing identity the
  system can distinguish. User account management is out of scope. The user making an
  HTTP request is referred to as the *caller* in the API contract.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A client can create a slot in a single interaction that completes without
  noticeable delay, with p99 latency ≤ 200 ms under the expected load.
- **SC-002**: The system prevents overlapping slots within the same calendar 100% of the
  time, including under concurrent requests.
- **SC-003**: A status change (free/busy) is reflected in every later read of that
  slot immediately after the change succeeds.
- **SC-004**: The system supports hundreds of users each holding thousands of slots, and
  listing or inspecting a single user's availability completes with p99 latency ≤ 300 ms
  regardless of total data volume.
- **SC-005**: Valid slot operations complete without server errors (5xx response rate
  below 0.1%), and every rejected operation returns a clear, actionable reason.

## Assumptions

- Each user has exactly one personal calendar.
  Support for multiple calendars is outside this feature's scope.
- Users are already identified by the platform. Authentication and account management are
  out of scope and handled by a separate concern.
- A new slot's default status is "free", representing availability.
- Slots use minute-level granularity. Sub-minute precision is not required.
- The maximum single-slot duration is 24 hours unless configured otherwise.
- Slots must start in the future. Creating slots in the past is not permitted.
- Two slots may not overlap within a calendar, regardless of their free/busy status.
- Converting a slot into a meeting, and querying aggregated availability across a time
  frame, are separate features and out of scope here.
- Deleting a slot removes it permanently.
  This feature has no soft-delete or undo operation.
- Time offsets supplied by users are honored on input and normalized for storage.
