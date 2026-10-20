# Phase 1 Data Model: Meeting Scheduling

## Overview

Adds one aggregate — `Meeting` — owned through the existing chain
`user → calendar → time_slot → meeting`. One meeting per slot is enforced by a database
`UNIQUE (slot_id)` constraint. A slot holding a meeting cannot be deleted (FK `RESTRICT`).
Participants are free-form rows (name and/or email) stored in a child table. No new PostgreSQL
extension is required (`btree_gist` is already installed).

## Domain Model (framework-free)

### Meeting (aggregate root)
- **Identity**: `MeetingId` (UUID)
- **Attributes**: `SlotId`, `title` (String), `description` (String, nullable),
  `participants` (`List<Participant>`, non-empty), `createdAt`, `updatedAt`
- **Invariants owned by the aggregate**:
  - title is present, non-blank, and ≤ 200 characters — FR-003, FR-011
  - description is optional. When present it is stored as provided (may be long)
  - at least one participant — FR-004, FR-011
  - a meeting always belongs to exactly one slot — FR-002

### Participant (value object)
- **Attributes**: `name` (optional, ≤ 100 chars), `email` (optional, ≤ 254 chars)
- **Invariants**: at least one of `name`/`email` present and non-blank. Either field, if
  present, must be non-blank and within its length bound
- **Equality**: by name + email (order-independent within a meeting)

### MeetingId (value object)
- Wraps `UUID`. Prevents primitive obsession and mixups with `SlotId`/`UserId`.

### Existing types reused
- **TimeSlot** (`SlotId`, `SlotStatus`): a meeting's slot. The slot's status is `BUSY` while a
  meeting exists and `FREE` afterwards. The existing overlap exclusion constraint is unaffected.

## Persistence Schema (Flyway `V2__meetings.sql`)

```sql
CREATE TABLE meetings (
    id          UUID PRIMARY KEY,
    slot_id     UUID NOT NULL REFERENCES time_slots (id) ON DELETE RESTRICT,
    title       TEXT NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_meetings_slot UNIQUE (slot_id),
    CONSTRAINT ck_meeting_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_meeting_title_len       CHECK (length(title) <= 200)
);

CREATE TABLE meeting_participants (
    meeting_id UUID NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    position   SMALLINT NOT NULL,
    name       TEXT,
    email      TEXT,
    PRIMARY KEY (meeting_id, position),
    CONSTRAINT ck_participant_present CHECK (
        (name IS NOT NULL AND length(btrim(name)) > 0)
        OR (email IS NOT NULL AND length(btrim(email)) > 0)
    ),
    CONSTRAINT ck_participant_name_len  CHECK (name  IS NULL OR length(name)  <= 100),
    CONSTRAINT ck_participant_email_len CHECK (email IS NULL OR length(email) <= 254)
);

CREATE INDEX ix_meeting_participants_meeting ON meeting_participants (meeting_id);
```

### Notes
- `uq_meetings_slot` is the structural guarantee behind SC-002: a second meeting for the same
  slot fails with SQLSTATE `23505` (`unique_violation`) even under concurrent requests.
- `ON DELETE RESTRICT` on `meetings.slot_id` makes FR-010 airtight: deleting a slot that holds a
  meeting fails with SQLSTATE `23503` (`foreign_key_violation`). The application also pre-checks
  for a friendly `409`.
- `ON DELETE CASCADE` on `meeting_participants.meeting_id` cleans up participants when a
  meeting is deleted (cancel). `meeting_participants` has no surrogate id. `(Meeting_id,
  position)` is the primary key and preserves ordering.
- Booking also updates the slot's `status` to `BUSY`. Cancellation sets it back to `FREE`. The
  adapter never writes generated columns.

## Validation Rules (from requirements)

| Rule | Source | Where enforced |
|------|--------|----------------|
| Meeting belongs to exactly one slot | FR-002 | schema (NOT NULL FK + UNIQUE) |
| At most one meeting per slot | FR-002, SC-002 | **DB `UNIQUE (slot_id)`** (+ adapter translates `23505`) |
| Title present, non-blank, ≤ 200 chars | FR-003, FR-011 | web (Bean Validation) + domain + DB check |
| Description optional, bounded length | FR-003 | web (Bean Validation) + domain |
| At least one participant | FR-004, FR-011 | web (Bean Validation) + domain + DB check per row |
| Each participant has name and/or email | FR-004 | web (Bean Validation) + domain + DB check |
| Slot must exist, be owned by caller, be FREE, hold no meeting | FR-005 | application (ownership) + domain + DB unique |
| Slot becomes unavailable on booking | FR-006 | application (same transaction) + domain |
| Slot modify/delete/status blocked while meeting exists | FR-010 | application guard + DB FK `RESTRICT` (delete) |
| Timestamps UTC with explicit offset | FR-015 | existing `OffsetDateTime` ↔ `timestamptz` mapping |

## State Transitions

```
[slot FREE, no meeting]
        --book(title, description, participants)--> slot BUSY, meeting exists
slot BUSY + meeting
   ├── view (read-only) ---------------------------> unchanged
   ├─X modify slot / change status (conflict)       (rejected 409)
   ├─X delete slot -> [absent]                       (rejected; DB RESTRICT backstop)
   └─cancel ------------------------------------> meeting removed, slot FREE

A slot that is BUSY (no meeting) is never bookable: --book--> rejected (409).
```

## Query Patterns

- **View meeting for a slot**: `WHERE slot_id = ?`, scoped to the caller's calendar through the
  slot (`time_slots.calendar_id = :callerCalendar`). Missing/foreign slot or no meeting → `404`.
- **Guard on slot mutation**: `SELECT EXISTS (SELECT 1 FROM meetings WHERE slot_id = ?)` before
  modify/delete/status-change.
- **Cancel**: delete the meeting row (participants cascade) and set the slot `FREE`, in one
  transaction.
- The unique index `uq_meetings_slot` serves both the existence check and the anti-double-book
  guarantee.

## State Transitions (slot status coupling)

```
FREE  --book meeting-->   BUSY
BUSY  --cancel meeting--> FREE
BUSY  --no meeting:      mark FREE is allowed by slot management (unrelated to meetings)
```
