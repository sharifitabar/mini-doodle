-- Track the last modification time of users and calendars, consistent with the other
-- aggregates (time_slots, meetings), which already carry created_at and updated_at.
-- No update operation exists for users or calendars yet, so the column is populated by its
-- default on insert; rows that predate this migration are backfilled from created_at rather
-- than the migration time.

ALTER TABLE users ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE users SET updated_at = created_at;
ALTER TABLE users
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE calendars ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE calendars SET updated_at = created_at;
ALTER TABLE calendars
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT now();
