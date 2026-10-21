-- mini-doodle transactional outbox: one durable record per calendar change.
-- The row is written in the same transaction as the state change; a poller delivers it
-- and marks the delivery state. The id gives the capture order for one aggregate.

CREATE TABLE outbox_events (
    id             BIGSERIAL PRIMARY KEY,
    event_id       UUID NOT NULL,
    event_type     TEXT NOT NULL,
    schema_version INTEGER NOT NULL,
    aggregate_type TEXT NOT NULL,
    aggregate_id   UUID NOT NULL,
    calendar_id    UUID,
    occurred_at    TIMESTAMPTZ NOT NULL,
    payload        JSONB NOT NULL,
    state          TEXT NOT NULL DEFAULT 'PENDING',
    attempts       INTEGER NOT NULL DEFAULT 0,
    last_error     TEXT,
    published_at   TIMESTAMPTZ,
    failed_at      TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id),
    CONSTRAINT ck_outbox_state CHECK (state IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_outbox_schema_version CHECK (schema_version >= 1),
    CONSTRAINT ck_outbox_event_type CHECK (event_type IN
        ('slot.created', 'slot.updated', 'slot.deleted', 'meeting.booked', 'meeting.cancelled')),
    CONSTRAINT ck_outbox_payload_object CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX ix_outbox_pending ON outbox_events (id) WHERE state = 'PENDING';
CREATE INDEX ix_outbox_published_at ON outbox_events (published_at) WHERE state = 'PUBLISHED';
