# Feature Specification: Meeting Scheduling

**Feature Branch**: `002-meeting-scheduling`

**Created**: 2026-10-16

**Status**: Complete

**Input**: User description: "Meeting scheduling: allow users to convert their available time slots into meetings with a title, description, and participants, and to view and cancel those meetings."

## Clarifications

### Session 2026-10-16

- Q: Are meeting participants references to existing platform users, or arbitrary free-form people? → A: Participants are recorded as provided. They need not correspond to existing platform users, and are not validated against known users.
- Q: Is meeting cancellation in scope for this feature? → A: Yes — canceling removes the meeting and releases its slot.
- Q: How should slot management behave for a slot that holds a meeting? → A: Reject conflicting slot operations (modify time/duration, delete, or change status) with a conflict error until the meeting is canceled.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Book a meeting from an available slot (Priority: P1)

A user selects one of their available (free) time slots.
They enter a title, an optional description, and one or more participants to book a meeting.
The meeting occupies the slot. The slot is no longer available.

**Why this priority**: The platform's main purpose is to convert declared availability into scheduled meetings.
Without booking, the service only manages time without meetings.

**Independent Test**: Create a free slot.
Book a meeting on it with a title and at least one participant.
Retrieve the meeting.
Make sure that it belongs to that slot and that the slot is no longer available.

**Acceptance Scenarios**:

1. **Given** a user with a free slot, **When** they book a meeting with a title and at least
   one participant, **Then** the meeting is created and associated with that slot, and the slot
   is no longer available.
2. **Given** a slot that already holds a meeting, **When** the user tries to book another
   meeting on it, **Then** the request is rejected and no second meeting is created.
3. **Given** a slot that is not free (busy and holding no meeting), **When** the user tries to
   book a meeting on it, **Then** the request is rejected.
4. **Given** a slot that belongs to another user or does not exist, **When** the user tries to
   book a meeting on it, **Then** the request is rejected and no meeting is created.
5. **Given** a booking request without a title or without any participant, **When** it is
   submitted, **Then** it is rejected as invalid.

---

### User Story 2 - View meeting details (Priority: P2)

A user views a scheduled meeting on one of their own slots.
The details include the title, description, participants, and slot time window.

**Why this priority**: After booking, users must be able to confirm what was scheduled and who
is involved. It is simpler than booking and delivers value on its own.

**Independent Test**: Book a meeting, then retrieve the meeting for its slot and make sure that the
title, description, participants, and time window match what was booked.

**Acceptance Scenarios**:

1. **Given** a slot that holds a meeting, **When** the owner requests the meeting, **Then** its
   title, description, participants, and the slot's time window are returned.
2. **Given** a slot that holds no meeting, **When** the owner requests the meeting, **Then**
   the request is rejected as not found.
3. **Given** a meeting on another user's slot, **When** a user requests it, **Then** the
   request is rejected and no meeting data is disclosed.

---

### User Story 3 - Cancel a meeting (Priority: P3)

A user cancels a scheduled meeting, removing it and releasing its slot back to available.

**Why this priority**: When plans change, users need to make time available again.
Cancellation is useful but is not required for booking.
It has a lower priority than booking and viewing.

**Independent Test**: Book a meeting, cancel it, then make sure that the meeting no longer exists and
the slot is available again.

**Acceptance Scenarios**:

1. **Given** a slot that holds a meeting, **When** the owner cancels the meeting, **Then** the
   meeting is removed and the slot becomes available again.
2. **Given** a slot that holds no meeting, **When** the owner tries to cancel it, **Then** the
   request is rejected as not found.
3. **Given** a meeting on another user's slot, **When** a user tries to cancel it, **Then** the
   request is rejected and nothing changes.

---

### Edge Cases

- Booking a meeting on a slot whose start time is in the past.
- Booking a meeting on a slot that is not free because it was explicitly marked busy.
- Booking the same slot concurrently from two requests (only one may succeed).
- Requests with a missing, empty, or whitespace-only title.
- Requests with no participants, an empty participant list, or duplicate participants.
- Participant entries with missing or blank details.
- Descriptions that are absent, empty, or very long.
- Titles or participant fields that exceed their allowed length.
- Retrieving or canceling a meeting for a slot owned by another user.
- Modifying the time or duration of, deleting, or changing the status of a slot that holds a
  meeting (all rejected as conflicts until the meeting is canceled).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to convert one of their available (free) time slots into a
  meeting by providing a title and at least one participant.
- **FR-002**: A meeting MUST be associated with exactly one time slot, and a slot MUST hold at
  most one meeting at any time.
- **FR-003**: A meeting MUST record a title (required) and MAY record a description (optional).
- **FR-004**: A meeting MUST record one or more participants as provided by the caller.
  A participant does not have to be an existing platform user or resolve to a known user.
- **FR-005**: The system MUST reject booking a slot that does not exist, is not owned by the
  caller, is not currently free, or already holds a meeting.
- **FR-006**: When a meeting is booked, its slot MUST become unavailable and MUST NOT be
  bookable again while the meeting exists.
- **FR-007**: Users MUST be able to retrieve the meeting associated with a slot they own,
  including its title, description, participants, and the slot's time window.
- **FR-008**: Users MUST NOT be able to view, book, or modify meetings on slots that belong to
  another user, nor learn of their existence.
- **FR-009**: Users MUST be able to cancel a meeting, which removes it and makes its slot
  available again.
- **FR-010**: While a slot holds a meeting, the system MUST reject these operations with a conflict error:
  change its time, duration, or status, or delete it.
  This restriction applies until the meeting is canceled.
- **FR-011**: The system MUST reject booking requests with a missing/empty title or with no
  participants.
- **FR-012**: All meeting data MUST be persisted so it survives application restarts.
- **FR-013**: When an operation is rejected, the system MUST return a clear, actionable error
  that distinguishes validation failures from authorization, not-found, and conflict cases.
- **FR-014**: The system MUST reject any request whose caller identity is missing or does not
  resolve to a known user, and MUST NOT treat an unknown caller as the owner of any meeting.
- **FR-015**: All date-times MUST be accepted and returned with explicit timezone offset
  information and stored unambiguously (in UTC), consistent with time slot management.

### Key Entities *(include if feature involves data)*

- **Meeting**: A booked engagement tied to exactly one time slot. Records a title (required), an
  optional description, one or more participants, a reference to its slot, and creation and
  last-update timestamps. At most one meeting exists per slot.
- **Participant**: A person involved in a meeting, other than or including the organizer,
  recorded as provided by the caller (for example, a name and/or contact detail). A participant need not
  be an existing platform user. A meeting has at least one participant.
- **Time Slot** (from time slot management): The block of time a meeting occupies. A slot's
  availability reflects whether it holds a meeting.
- **User** (from time slot management): The owner of the calendar and slots, referred to as the
  *caller* in the API contract.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A client can book a meeting in a single interaction that completes without
  noticeable delay, with p99 latency ≤ 200 ms under the expected load.
- **SC-002**: No slot ever holds more than one meeting, 100% of the time, including under
  concurrent booking requests.
- **SC-003**: After a booking succeeds, the slot is reflected as unavailable in every
  later read immediately after the change.
- **SC-004**: The system supports hundreds of users each holding thousands of slots, and
  retrieving a meeting completes with p99 latency ≤ 300 ms regardless of total data volume.
- **SC-005**: Valid meeting operations complete without server errors (5xx response rate below
  0.1%), and every rejected operation returns a clear, actionable reason.

## Assumptions

- Users are already identified by the platform. Authentication and account management are out
  of scope and handled by a separate concern, exactly as in time slot management.
- Each user has exactly one personal calendar.
  Support for multiple calendars is outside the scope.
- A meeting requires an existing free slot.
  There is no separate "create a meeting from scratch" flow.
  Booking does not create availability.
- A meeting uses its slot's time window.
  Meetings do not have an independent time range.
- Notifications and invitations (email/calendar invites), RSVP responses, recurring meetings,
  locations, and video-conference links are out of scope for this feature.
- Meeting participants are recorded as provided and need not be existing platform users.
  Notifying participants or requiring their acceptance is out of scope.
- The aggregated free/busy availability query across a time frame is a separate feature and out
  of scope here.
- A meeting is only bookable on a slot starting in the future. Past slots cannot be booked.
- Time slots and their ownership rules (ownership isolation, no overlap, future start, UTC
  storage) are defined by the time slot management feature and are reused unchanged.
