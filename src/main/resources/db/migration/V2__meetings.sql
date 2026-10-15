-- mini-doodle meeting scheduling: at most one meeting per time slot.
-- The UNIQUE (slot_id) constraint makes double-booking structurally impossible, and the
-- RESTRICT foreign key prevents a slot that holds a meeting from being deleted.

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
