# Phase 1 Data Model: Time Slot Management

## Overview

Three persisted entities — `users`, `calendars`, `time_slots` — plus the framework-free
domain model that mirrors them. Ownership flows `user → calendar → slot`. The overlap rule
is enforced by a database exclusion constraint, not by application code alone.

## Domain Model (framework-free)

### Calendar
- **Identity**: `CalendarId` (UUID)
- **Owner**: `UserId`
- **Attributes**: `createdAt`, `updatedAt`
- **Rules**: exactly one calendar per user in this feature.

### TimeSlot (aggregate root)
- **Identity**: `SlotId` (UUID)
- **Attributes**: `CalendarId`, `TimeRange`, `SlotStatus`
- **Invariants owned by the aggregate**:
  - duration is positive and ≤ configured maximum (default 24h) — FR-011
  - start is in the future at creation/modification time — FR-012
  - status defaults to `FREE` — FR-003
  - status transitions are idempotent (setting the current status is a no-op success)
  - overlap with another slot in the same calendar is illegal — FR-010 (ultimately enforced
    by the database exclusion constraint)

### TimeRange (value object)
- **Attributes**: `start` (`OffsetDateTime`, normalized to UTC), `duration` (minutes)
- **Derived**: `end = start + duration`
- **Equality**: by start + duration

### SlotStatus (enum)
- `FREE`, `BUSY`

### UserId / CalendarId / SlotId (value objects)
- Wrap `UUID`. Prevent primitive obsession and cross-id mixups.

## Persistence Schema (Flyway `V1__init.sql`, `users`/`calendars` `updated_at` added by `V3__add_updated_at_to_users_and_calendars.sql`)

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE users (
    id          UUID PRIMARY KEY,
    display_name TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE calendars (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_calendars_user UNIQUE (user_id)
);

CREATE TABLE time_slots (
    id               UUID PRIMARY KEY,
    calendar_id      UUID NOT NULL REFERENCES calendars (id) ON DELETE CASCADE,
    start_at         TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER NOT NULL,
    status           TEXT NOT NULL DEFAULT 'FREE',
    slot_range       TSTZRANGE GENERATED ALWAYS AS
                       (tstzrange(start_at,
                                  ((start_at AT TIME ZONE 'UTC')
                                     + make_interval(mins => duration_minutes))
                                     AT TIME ZONE 'UTC',
                                  '[)')) STORED,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_duration_positive CHECK (duration_minutes > 0),
    CONSTRAINT ck_duration_max CHECK (duration_minutes <= 1440),
    CONSTRAINT ck_status_valid CHECK (status IN ('FREE', 'BUSY')),
    CONSTRAINT ex_slot_no_overlap
        EXCLUDE USING gist (calendar_id WITH =, slot_range WITH &&)
);

CREATE INDEX ix_time_slots_calendar_start ON time_slots (calendar_id, start_at);
CREATE INDEX ix_time_slots_range ON time_slots USING gist (slot_range);
```

### Notes
- Every table tracks both `created_at` and `updated_at`. `users` and `calendars` gained
  `updated_at` in `V3__add_updated_at_to_users_and_calendars.sql`. There is no update
  operation for them yet, so the value defaults to `now()` on insert (existing rows were
  backfilled from `created_at`).
- `btree_gist` is required so a GIST exclusion constraint can mix an equality column
  (`calendar_id`) with a range overlap operator (`&&`).
- `[)` bounds make adjacent slots (one ending exactly when the next begins) **not**
  overlap, which matches scheduling intuition.
- The `ex_slot_no_overlap` constraint raises PostgreSQL SQLSTATE `23P01`
  (`exclusion_violation`). The persistence adapter translates it to a domain overlap
  exception → HTTP 409.
- The generated `slot_range` column is not written by the application. JOOQ is configured
  to exclude generated columns from insert/update.
- The generated expression normalizes through `AT TIME ZONE 'UTC'` before adding the
  interval because PostgreSQL requires generated columns (and exclusion constraints) to use
  IMMUTABLE expressions. `timestamptz + interval` is STABLE (DST-dependent) and is rejected
  with `ERROR: generation expression is not immutable`. Converting to a UTC `timestamp`,
  adding the interval, then converting back is IMMUTABLE and yields the same end instant.

## Validation Rules (from requirements)

| Rule | Source | Where enforced |
|------|--------|----------------|
| Start must be a valid offset date-time | FR-001, FR-014 | web (Bean Validation) + domain |
| Duration positive and ≤ 24h (configurable) | FR-011 | domain + DB check |
| Start in the future | FR-012 | domain |
| No overlap in same calendar | FR-010 | **DB exclusion constraint** (+ domain pre-check for clear errors) |
| Default status FREE | FR-003 | domain + DB default |
| Ownership isolation | FR-009 | application (calendar lookup by caller) |
| Slot belongs to exactly one calendar | FR-008 | schema (NOT NULL FK) |

## State Transitions

```
[absent] --create--> FREE
FREE <--setStatus--> BUSY
FREE|BUSY --modify(start/duration)--> FREE|BUSY  (same status, new range)
FREE|BUSY --delete--> [absent]
```

- Modifying time/duration re-validates all range invariants and the overlap constraint.
- Status changes do not affect the range.

## Query Patterns

- **List a user's slots**: filter by the caller's `calendar_id`, optional `from`/`to`
  overlap window (`slot_range && tstzrange(from,to,'[)')`) and optional `status`. Ordered
  by `start_at`. Bounded by `limit`/`offset`.
- **Get/Modify/Delete one slot**: by `id` scoped to the caller's `calendar_id`.
- Indexes `ix_time_slots_calendar_start` and `ix_time_slots_range` support these patterns.
