# Phase 0 Research: Meeting Scheduling

All Technical Context unknowns are resolved below (no `NEEDS CLARIFICATION` remain). Decisions
build on the locked stack and the conventions established for time slot management.

## Decision: Extend the `calendar` bounded context (no new context)

**Reason**: A meeting is meaningless without its slot and is reached through the same
calendar ownership chain (`user → calendar → slot → meeting`). Reusing the context keeps
ownership checks, error handling, and persistence conventions in one place.

**Alternatives considered**: A separate `meeting` bounded context (rejected: no independent
lifecycle, would duplicate ownership/error connections between components). Sharing a database but separate module
(rejected: multi-module cost with no boundary benefit — constitution IV).

## Decision: One meeting per slot, enforced by a `UNIQUE (slot_id)` constraint

**Reason**: Constitution II and SC-002. Booking is a race: two concurrent requests can both
read the slot as FREE. The database makes double-booking structurally impossible:

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
    CONSTRAINT ck_meeting_title_len CHECK (length(title) <= 200)
);
```

The second concurrent insert violates `uq_meetings_slot` (SQLSTATE `23505`), which the
persistence adapter translates to a domain conflict → HTTP `409`.

**Alternatives considered**: A `booked` flag on `time_slots` only (rejected: mixes meeting data
into the slot table and cannot hold title/participants). Application `SELECT ... FOR UPDATE`
then insert (rejected: extra round trip and lock discipline, per constitution II).

## Decision: Slot deletion with a meeting is blocked by a `RESTRICT` foreign key

**Reason**: FR-010 requires that a slot holding a meeting cannot be deleted. `ON DELETE
RESTRICT` makes the deletion fail at the database (SQLSTATE `23503`), so no application bug can
orphan a meeting. The adapter maps it to a `409` conflict. Modify and status-change are
enforced in application services with a single existence check (see below), because they are
consistency rules rather than a data-integrity race.

**Alternatives considered**: `ON DELETE CASCADE` from slot to meeting (rejected: silently
destroys a meeting, contradicting FR-010). A database trigger to reject slot updates when a
meeting exists (rejected: more moving parts than the guarded application path needs).

## Decision: Participants are free-form rows (name and/or email), not user references

**Reason**: Spec clarification (2026-10-16): participants are recorded as provided and need
not resolve to known users. A child table keeps the meeting aggregate cohesive and lets us
enforce "at least one participant" at the application/aggregate level:

```sql
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
```

`position` preserves the caller's ordering. Participants are replaced wholesale on write (no
independent edit in this feature).

**Alternatives considered**: A `jsonb` array of participants on `meetings` (rejected: weaker
constraints, harder to index/validate). A join table to `users` (rejected: contradicts the
clarification and forces accounts on invitees).

## Decision: Booking is one transaction that also marks the slot BUSY

**Reason**: FR-006 — the slot must become unavailable atomically with booking, so a crash
between the two writes cannot leave a FREE slot that has a meeting. The application service is
the transaction boundary (matching existing services): validate ownership + FREE status, insert
the meeting, update the slot status to `BUSY`. Cancellation is the inverse (delete meeting, set
slot `FREE`) in one transaction.

**Alternatives considered**: Deriving slot availability from the meeting's existence and
dropping the status write (rejected: slot status is independently user-settable and the list
filter already depends on it, keeping it updated preserves existing read behavior). Saga/outbox
(rejected: single database, no need).

## Decision: Slot-scoped endpoints under `/api/v1/slots/{slotId}/meeting`

**Reason**: Matches the README roadmap (`POST /slots/{slotId}/meeting`) and the "one meeting
per slot" model, so the meeting needs no separate id in the URL. `POST` books, `GET` views,
`DELETE` cancels.

**Alternatives considered**: A top-level `/api/v1/meetings` collection (rejected: implies
meetings exist independently of slots and adds id-based ownership checks without benefit at
this scope).

## Decision: Modify/status-change guards in the slot service

**Reason**: FR-010. `SlotApplicationService` (its update and delete operations) consults
`MeetingRepository.existsBySlotId(...)` and rejects with a conflict before writing, with the
`RESTRICT` FK as the backstop for deletion.
Injecting one outbound port into that service is the smallest change that keeps the rule with
the operation it constrains.

**Alternatives considered**: Marking slots read-only through a DB view/trigger (rejected: obscures
the rule and complicates jOOQ mapping).

## Decision: Error mapping and ownership reuse the existing RFC 7807 model

**Reason**: FR-013/FR-008. A meeting on a slot outside the caller's calendar returns `404`
(do not leak existence), consistent with slot management. Unknown/missing caller → `403`/`400`.
Validation (blank title, no participants, over-length) → `400`. Conflicts (slot not free,
already has a meeting, slot holds a meeting) → `409`. New exceptions extend the existing
`DomainException` hierarchy and are handled by `GlobalExceptionHandler`.

**Alternatives considered**: `422` for validation (rejected: `400` is the established default
here). `403` for another user's slot (rejected: would leak that the slot exists).

## Decision: Observability and documentation parity

**Reason**: Constitution V. Add a `MeetingMetrics` component (booked/viewed/canceled
counters + timers) mirroring `SlotMetrics`, structured logs with `event=`/`key=value`, extend
`contracts/openapi.yaml` with the meeting operations and schemas, and update README + Swagger
UI annotations in the same change.

**Alternatives considered**: Rely on generic HTTP metrics only (rejected: the domain counters
are the useful signal and match the existing pattern).

## Decision: Testing strategy

**Reason**: Constitution III. Layers:
1. Domain unit tests: title/participant invariants on `Meeting` and `Participant`.
2. Testcontainers integration tests: `uq_meetings_slot` rejects a second meeting for a slot.
   `RESTRICT` FK blocks deleting a slot that holds a meeting. Deleting a meeting cascades
   participants. A concurrent-booking test where exactly one of two parallel bookings succeeds.
3. MockMvc contract tests: book/view/cancel happy paths plus `400`/`404`/`409` and the
   modify/delete/status guards on a booked slot.

**Alternatives considered**: H2/embedded (rejected: cannot prove the unique/FK constraints).
Service-only mocks (rejected: would miss the concurrency guarantee that is the core claim).
