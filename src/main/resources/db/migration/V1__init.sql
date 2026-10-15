-- mini-doodle initial schema: users, calendars, time_slots
-- Overlap prevention is enforced by a GIST exclusion constraint over a tstzrange.

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE users (
    id           UUID PRIMARY KEY,
    display_name TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE calendars (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
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
