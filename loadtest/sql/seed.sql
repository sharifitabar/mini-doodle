-- loadtest/sql/seed.sql — provision the load-test dataset (feature 007).
--
-- Invoked by the `seed` compose service:
--   docker compose --profile loadtest run --rm seed                       # seed.sql
--   docker compose --profile loadtest run --rm seed /sql/reset.sql        # reset.sql
--
-- Parameterised by the psql variable `user_count`, which the compose entrypoint sets from
-- SEED_USERS (default 1000). Run it by hand as:
--   psql -h localhost -U minidoodle -d minidoodle -v ON_ERROR_STOP=1 -v user_count=1000 -f seed.sql
--
-- The dataset is synthetic and deterministic: every row's primary key is derived from the user
-- number `n` and a per-user row index `i`, so a re-run inserts nothing (see ON CONFLICT DO
-- NOTHING) and one run's rows can be recognised by their UUID block alone. All writes are a
-- single transaction: a failed seed leaves the database exactly as it was.
--
-- Shape (data-model.md §1):
--   users(n)      = 1 calendar, slots(n) = 600 + (n*37) mod 201  -> 600..800 slots, busiest at 800
--   meetings(n)   = (n*13) mod 101                               -> 0..100 meetings per user,
--                                                                   placed on that user's
--                                                                   lowest-indexed slots
--   participants  = 2 per meeting
--
-- Every slot is back-dated off T0 = date_trunc('hour', now()) on a 45-minute grid with 30-minute
-- durations, so the seeded history never overlaps and never occupies the future. The future is
-- what the manipulation flow writes into (data-model.md §5).
--
-- UUID blocks (see the boundary note in verify.sql and data-model.md §2):
--   users     10000000-0000-4000-8000-000000000{n}
--   calendars 20000000-0000-4000-8000-000000000{n}
--   slots     30000000-0000-4000-8000-{lpad(to_hex((n-1)*1000 + i + 1), 12, '0')}
--   meetings  40000000-0000-4000-8000-{same}
-- Only the users block is ever used as a *predicate*; the rest are naming conventions, because
-- rows written later by the application (the manipulation flow) carry random UUIDs.

\set ON_ERROR_STOP on
\timing on

BEGIN;

-- Durability, not integrity: losing the tail of a seed to a crash costs a re-run, never a wrong
-- dataset. Nothing here relaxes a constraint.
SET LOCAL synchronous_commit = off;

-- T0 is captured once, so every slot in the dataset sits on one consistent grid.
SELECT clock_timestamp() AS seed_started_at, date_trunc('hour', now()) AS t0 \gset

-- 1. Load users --------------------------------------------------------------------------------
INSERT INTO users (id, display_name)
SELECT ('10000000-0000-4000-8000-' || lpad(to_hex(n::bigint), 12, '0'))::uuid,
       'Load User ' || n
FROM generate_series(1, :user_count) AS n
ON CONFLICT DO NOTHING;

-- 2. One calendar per load user (uq_calendars_user makes this structural) ----------------------
INSERT INTO calendars (id, user_id)
SELECT ('20000000-0000-4000-8000-' || lpad(to_hex(n::bigint), 12, '0'))::uuid,
       ('10000000-0000-4000-8000-' || lpad(to_hex(n::bigint), 12, '0'))::uuid
FROM generate_series(1, :user_count) AS n
ON CONFLICT DO NOTHING;

-- 3. Slot history: 600..800 past-dated slots per user ------------------------------------------
-- Slot index i is back-dated (i + 1) * 45 minutes, shifted by (n mod 15) minutes so users do not
-- share one indistinguishable grid. 45-minute spacing with 30-minute durations leaves a 15-minute
-- gap, so the GiST exclusion constraint (ex_slot_no_overlap) is satisfied by construction.
INSERT INTO time_slots (id, calendar_id, start_at, duration_minutes, status)
SELECT ('30000000-0000-4000-8000-'
            || lpad(to_hex(((n - 1) * 1000 + i + 1)::bigint), 12, '0'))::uuid,
       ('20000000-0000-4000-8000-' || lpad(to_hex(n::bigint), 12, '0'))::uuid,
       :'t0'::timestamptz - make_interval(mins => (i + 1) * 45 - (n % 15)),
       30,
       'FREE'
FROM generate_series(1, :user_count) AS n
CROSS JOIN LATERAL generate_series(0, 600 + (n * 37) % 201 - 1) AS i
ON CONFLICT DO NOTHING;

-- 4. Flip the meeting-bearing slots to BUSY ----------------------------------------------------
-- The same lowest-i slots the meetings below land on, so the seeded state is exactly the state
-- POST /api/v1/slots/{id}/meeting leaves behind: BUSY slot + meeting on it (invariant I5).
-- `status = 'FREE'` makes the flip idempotent and keeps it from re-touching rows.
UPDATE time_slots s
   SET status = 'BUSY'
 WHERE s.status = 'FREE'
   AND s.id IN (
       SELECT ('30000000-0000-4000-8000-'
                   || lpad(to_hex(((n - 1) * 1000 + i + 1)::bigint), 12, '0'))::uuid
       FROM generate_series(1, :user_count) AS n
       CROSS JOIN LATERAL generate_series(0, (n * 13) % 101 - 1) AS i
   );

-- 5. Meetings: (n*13) mod 101 per user, on that user's lowest-indexed slots --------------------
INSERT INTO meetings (id, slot_id, title, description)
SELECT ('40000000-0000-4000-8000-'
            || lpad(to_hex(((n - 1) * 1000 + i + 1)::bigint), 12, '0'))::uuid,
       ('30000000-0000-4000-8000-'
            || lpad(to_hex(((n - 1) * 1000 + i + 1)::bigint), 12, '0'))::uuid,
       'Load meeting ' || n || '-' || i,
       'Synthetic meeting for load-test user ' || n
FROM generate_series(1, :user_count) AS n
CROSS JOIN LATERAL generate_series(0, (n * 13) % 101 - 1) AS i
ON CONFLICT DO NOTHING;

-- 6. Two participants per meeting (ck_participant_present needs a name or an email) -------------
-- Derived from the same (n, i) predicate rather than from a join back to meetings, so it stays a
-- single pass; ON CONFLICT DO NOTHING keys off PRIMARY KEY (meeting_id, position).
INSERT INTO meeting_participants (meeting_id, position, name, email)
SELECT ('40000000-0000-4000-8000-'
            || lpad(to_hex(((n - 1) * 1000 + i + 1)::bigint), 12, '0'))::uuid,
       p.position,
       'Participant ' || p.position || ' of ' || n,
       'participant' || p.position || '.user' || n || '@loadtest.local'
FROM generate_series(1, :user_count) AS n
CROSS JOIN LATERAL generate_series(0, (n * 13) % 101 - 1) AS i
CROSS JOIN (VALUES (0), (1)) AS p(position)
ON CONFLICT DO NOTHING;

COMMIT;

-- 7. Fresh statistics before any load (invariant I8) -------------------------------------------
-- Deliberately after COMMIT: ANALYZE is not part of the dataset's atomicity, and it should sample
-- the committed rows. Without statistics the planner can prefer a sequential scan over
-- (calendar_id, start_at) or the GiST range index, and the measured latency would describe a
-- planning accident rather than the service.
ANALYZE users, calendars, time_slots, meetings, meeting_participants;

-- 8. Provisioning report (FR-011, FR-012) ------------------------------------------------------
-- Scoped by load-user *ownership*: the seeded ids are deterministic, but anything the application
-- wrote during an earlier run carries a random UUID, and counting by id block would silently fold
-- those rows (and the demo user) into the totals.
CREATE TEMP TABLE load_users AS
SELECT id FROM users
 WHERE id >= '10000000-0000-4000-8000-000000000000'
   AND id < '10000000-0000-4000-8001-000000000000';

SELECT 'elapsed' AS metric, (clock_timestamp() - :'seed_started_at'::timestamptz)::text AS value
UNION ALL
SELECT 'users', count(*)::text FROM load_users
UNION ALL
SELECT 'calendars', count(*)::text
  FROM calendars WHERE user_id IN (SELECT id FROM load_users)
UNION ALL
SELECT 'slots', count(*)::text
  FROM time_slots s JOIN calendars k ON k.id = s.calendar_id
 WHERE k.user_id IN (SELECT id FROM load_users)
UNION ALL
SELECT 'busy_slots', count(*)::text
  FROM time_slots s JOIN calendars k ON k.id = s.calendar_id
 WHERE k.user_id IN (SELECT id FROM load_users) AND s.status = 'BUSY'
UNION ALL
SELECT 'meetings', count(*)::text
  FROM meetings m JOIN time_slots s ON s.id = m.slot_id JOIN calendars k ON k.id = s.calendar_id
 WHERE k.user_id IN (SELECT id FROM load_users)
UNION ALL
SELECT 'participants', count(*)::text
  FROM meeting_participants p JOIN meetings m ON m.id = p.meeting_id
  JOIN time_slots s ON s.id = m.slot_id JOIN calendars k ON k.id = s.calendar_id
 WHERE k.user_id IN (SELECT id FROM load_users)
UNION ALL
SELECT 'max_slots_per_user', coalesce(max(c), 0)::text FROM (
    SELECT count(s.id) AS c
      FROM load_users l
      JOIN calendars k ON k.user_id = l.id
      LEFT JOIN time_slots s ON s.calendar_id = k.id
     GROUP BY l.id) t
UNION ALL
SELECT 'max_meetings_per_user', coalesce(max(c), 0)::text FROM (
    SELECT count(m.id) AS c
      FROM load_users l
      JOIN calendars k ON k.user_id = l.id
      LEFT JOIN time_slots s ON s.calendar_id = k.id
      LEFT JOIN meetings m ON m.slot_id = s.id
     GROUP BY l.id) t
ORDER BY metric;
