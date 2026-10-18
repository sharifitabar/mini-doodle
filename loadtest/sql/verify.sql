-- loadtest/sql/verify.sql — assert the seeded dataset's invariants before load starts.
--
--   docker compose --profile loadtest run --rm seed /sql/verify.sql
--
-- Invariants I1–I8 from data-model.md §4. Each one is written to FAIL LOUDLY: a violation raises
-- an exception, psql exits non-zero (ON_ERROR_STOP), and run.sh stops before a single request is
-- sent. A measurement taken against a dataset that does not match the stated scale would be
-- worthless, so this file is the difference between "we measured 1000 users" and "we think we did".
--
-- Deliberately parameter-free: it never needs to know SEED_USERS, so the same file validates a
-- 10-user smoke dataset and a 1000-user run. Scale-dependent assertions (the 800-slot and
-- 100-meeting ceilings) are conditioned on the dataset size instead of being skipped silently.
--
-- THE BOUNDARY, because this is where the design was wrong first time round. The load users are
-- the single source of truth for "what is load-test data": every other check and every delete
-- joins through them. Two traps make that the only safe rule:
--
--   1. The demo user is `11111111-1111-1111-1111-111111111111`. A "10000000-… to 20000000-…"
--      range — the obvious way to read the UUID blocks — *contains* it, so a reset built on that
--      range deletes the demo user. The real block is the tight one below
--      (`10000000-0000-4000-8000-*`), which excludes it and still covers every generated id.
--   2. Rows the application writes later get random UUIDs, and a random v4 UUID starts with
--      `3` about 1 time in 16 — so the slot and meeting blocks are naming conventions, never
--      predicates. Everything is scoped by ownership (`calendars.user_id`) instead.
--
-- I9 (a re-seed changes nothing) cannot be observed from a single pass — it is checked by running
-- seed.sql twice and comparing its report; see specs/007-k6-load-test/quickstart.md.

\set ON_ERROR_STOP on
\timing on

-- The definition of "load-test user", used by every assertion below.
CREATE TEMP TABLE load_users AS
SELECT id FROM users
 WHERE id >= '10000000-0000-4000-8000-000000000000'
   AND id < '10000000-0000-4000-8001-000000000000';

-- Per-user shape, computed once and reused. At most one meeting per slot (uq_meetings_slot), so
-- these LEFT JOINs cannot fan out.
CREATE TEMP TABLE load_user_shape AS
SELECT l.id AS user_id,
       count(s.id) AS slots,
       count(m.id) AS meetings
  FROM load_users l
  LEFT JOIN calendars k ON k.user_id = l.id
  LEFT JOIN time_slots s ON s.calendar_id = k.id
  LEFT JOIN meetings m ON m.slot_id = s.id
 GROUP BY l.id;

-- I1: load users exist and each has exactly one calendar.
DO $$
DECLARE
    v_users     bigint;
    v_calendars bigint;
BEGIN
    SELECT count(*) INTO v_users FROM load_users;
    IF v_users = 0 THEN
        RAISE EXCEPTION 'I1 violated: no load users in the 10000000-0000-4000-8000-* block. Run seed.sql first.';
    END IF;

    SELECT count(*) INTO v_calendars
      FROM calendars WHERE user_id IN (SELECT id FROM load_users);
    IF v_calendars <> v_users THEN
        RAISE EXCEPTION 'I1 violated: % load users but % load calendars (expected exactly one each).',
            v_users, v_calendars;
    END IF;

    RAISE NOTICE 'I1 ok: % load users, one calendar each', v_users;
END $$;

-- I2: no overlapping slots in the seeded history. The GiST exclusion constraint makes this
-- impossible to violate on insert, but a rule that is only enforced by a constraint we believe
-- exists is a rule nobody has checked; this reads the data back.
DO $$
DECLARE
    v_overlaps bigint;
BEGIN
    SELECT count(*) INTO v_overlaps FROM (
        SELECT s.start_at,
               lag(s.start_at + make_interval(mins => s.duration_minutes))
                   OVER (PARTITION BY s.calendar_id ORDER BY s.start_at) AS previous_end
          FROM time_slots s
          JOIN calendars k ON k.id = s.calendar_id
         WHERE k.user_id IN (SELECT id FROM load_users)) t
     WHERE previous_end IS NOT NULL
       AND start_at < previous_end;
    IF v_overlaps > 0 THEN
        RAISE EXCEPTION 'I2 violated: % overlapping slot pairs in the seeded history.', v_overlaps;
    END IF;
    RAISE NOTICE 'I2 ok: no overlapping slots';
END $$;

-- I3: 600..800 slots per user (FR-007), with the busiest users at the ceiling.
DO $$
DECLARE
    v_min     bigint;
    v_max     bigint;
    v_ceiling bigint;
    v_users   bigint;
BEGIN
    SELECT min(slots), max(slots), count(*) FILTER (WHERE slots = 800)
      INTO v_min, v_max, v_ceiling
      FROM load_user_shape;
    IF v_min < 600 OR v_max > 800 THEN
        RAISE EXCEPTION 'I3 violated: slots per user range %-%, outside the required 600-800.',
            v_min, v_max;
    END IF;

    -- The formula first reaches 200 (= the 800 ceiling) at user 38, so a smaller dataset cannot
    -- contain the ceiling without being wrong. Silence below that, assert above it.
    SELECT count(*) INTO v_users FROM load_users;
    IF v_users >= 40 AND v_ceiling = 0 THEN
        RAISE EXCEPTION 'I3 violated: no user at the 800-slot ceiling (FR-007) across % users.',
            v_users;
    END IF;
    RAISE NOTICE 'I3 ok: %-% slots per user, % at the 800 ceiling', v_min, v_max, v_ceiling;
END $$;

-- I4: at most 100 meetings per user (FR-008), never more meetings than slots.
DO $$
DECLARE
    v_max        bigint;
    v_users      bigint;
    v_at_ceiling bigint;
BEGIN
    SELECT max(meetings) INTO v_max FROM load_user_shape;
    IF v_max > 100 THEN
        RAISE EXCEPTION 'I4 violated: a user has % meetings, above the 100 target.', v_max;
    END IF;
    IF EXISTS (SELECT 1 FROM load_user_shape WHERE meetings > slots) THEN
        RAISE EXCEPTION 'I4 violated: a user has more meetings than slots.';
    END IF;

    -- The formula first reaches 100 at user 31; same conditioning as I3.
    SELECT count(*) INTO v_users FROM load_users;
    SELECT count(*) FILTER (WHERE meetings = 100) INTO v_at_ceiling FROM load_user_shape;
    IF v_users >= 31 AND v_at_ceiling = 0 THEN
        RAISE EXCEPTION 'I4 violated: no user at the 100-meeting target across % users.', v_users;
    END IF;
    RAISE NOTICE 'I4 ok: at most % meetings per user, % at the 100 ceiling', v_max, v_at_ceiling;
END $$;

-- I5: the seeded state matches what the booking use case produces — every meeting sits on a BUSY
-- slot, and no BUSY slot in a load calendar lacks a meeting.
DO $$
DECLARE
    v_free_with_meeting    bigint;
    v_busy_without_meeting bigint;
BEGIN
    SELECT count(*) INTO v_free_with_meeting
      FROM meetings m
      JOIN time_slots s ON s.id = m.slot_id
      JOIN calendars k ON k.id = s.calendar_id
     WHERE k.user_id IN (SELECT id FROM load_users)
       AND s.status <> 'BUSY';
    IF v_free_with_meeting > 0 THEN
        RAISE EXCEPTION 'I5 violated: % meetings sit on non-BUSY slots.', v_free_with_meeting;
    END IF;

    SELECT count(*) INTO v_busy_without_meeting
      FROM time_slots s
      JOIN calendars k ON k.id = s.calendar_id
     WHERE k.user_id IN (SELECT id FROM load_users)
       AND s.status = 'BUSY'
       AND NOT EXISTS (SELECT 1 FROM meetings m WHERE m.slot_id = s.id);
    IF v_busy_without_meeting > 0 THEN
        RAISE EXCEPTION 'I5 violated: % BUSY load slots hold no meeting.', v_busy_without_meeting;
    END IF;
    RAISE NOTICE 'I5 ok: BUSY slots and meetings agree';
END $$;

-- I6: every meeting carries a participant that satisfies ck_participant_present, and the seeded
-- meetings carry exactly two.
DO $$
DECLARE
    v_without bigint;
    v_wrong   bigint;
BEGIN
    SELECT count(*) INTO v_without
      FROM meetings m
      JOIN time_slots s ON s.id = m.slot_id
      JOIN calendars k ON k.id = s.calendar_id
     WHERE k.user_id IN (SELECT id FROM load_users)
       AND NOT EXISTS (
           SELECT 1 FROM meeting_participants p
            WHERE p.meeting_id = m.id
              AND (btrim(coalesce(p.name, '')) <> '' OR btrim(coalesce(p.email, '')) <> ''));
    IF v_without > 0 THEN
        RAISE EXCEPTION 'I6 violated: % meetings have no participant with a name or an email.', v_without;
    END IF;

    SELECT count(*) INTO v_wrong
      FROM meetings m
      JOIN time_slots s ON s.id = m.slot_id
      JOIN calendars k ON k.id = s.calendar_id
     WHERE k.user_id IN (SELECT id FROM load_users)
       AND (SELECT count(*) FROM meeting_participants p WHERE p.meeting_id = m.id) <> 2;
    IF v_wrong > 0 THEN
        RAISE EXCEPTION 'I6 violated: % seeded meetings do not have exactly 2 participants.', v_wrong;
    END IF;
    RAISE NOTICE 'I6 ok: every meeting has participants';
END $$;

-- I7: the seeded past stays in the past, and the load calendars' future is empty — that emptiness
-- is exactly the space the manipulation flow writes into. A stale future turns every create-slot
-- into a 409 and would measure a write path that never wrote (research D13).
DO $$
DECLARE
    v_future_any bigint;
    v_first_slot timestamptz;
BEGIN
    SELECT count(*), min(s.start_at) INTO v_future_any, v_first_slot
      FROM time_slots s
      JOIN calendars k ON k.id = s.calendar_id
     WHERE k.user_id IN (SELECT id FROM load_users)
       AND s.start_at >= now();
    IF v_future_any > 0 THEN
        RAISE EXCEPTION
            'I7 violated: load calendars already hold % future slots (earliest %) — run reset.sql first, or the manipulation flow will measure 409s.',
            v_future_any, v_first_slot;
    END IF;
    RAISE NOTICE 'I7 ok: history is past-dated, the future is free';
END $$;

-- I8: the planner has real statistics to work from, rather than a planning accident.
DO $$
DECLARE
    v_reltuples real;
BEGIN
    SELECT reltuples INTO v_reltuples FROM pg_class WHERE oid = 'time_slots'::regclass;
    IF v_reltuples IS NULL OR v_reltuples <= 0 THEN
        RAISE EXCEPTION 'I8 violated: no statistics on time_slots — seed.sql did not ANALYZE.';
    END IF;
    RAISE NOTICE 'I8 ok: time_slots statistics present (reltuples ~ %)', round(v_reltuples);
END $$;

-- Shape report, so a failing run and a passing run both say what they were looking at.
SELECT 'load users' AS entity, count(*)::text AS value FROM load_users
UNION ALL
SELECT 'slots per user min', min(slots)::text FROM load_user_shape
UNION ALL
SELECT 'slots per user max', max(slots)::text FROM load_user_shape
UNION ALL
SELECT 'users at 800-slot ceiling', count(*) FILTER (WHERE slots = 800)::text FROM load_user_shape
UNION ALL
SELECT 'meetings per user max', max(meetings)::text FROM load_user_shape
UNION ALL
SELECT 'users at 100-meeting target', count(*) FILTER (WHERE meetings = 100)::text
  FROM load_user_shape
UNION ALL
SELECT 'participants total', count(*)::text
  FROM meeting_participants p
  JOIN meetings m ON m.id = p.meeting_id
  JOIN time_slots s ON s.id = m.slot_id
  JOIN calendars k ON k.id = s.calendar_id
 WHERE k.user_id IN (SELECT id FROM load_users)
UNION ALL
SELECT 'future slots on load calendars', count(*)::text
  FROM time_slots s
  JOIN calendars k ON k.id = s.calendar_id
 WHERE k.user_id IN (SELECT id FROM load_users)
   AND s.start_at >= now()
UNION ALL
SELECT 'demo user survived', (SELECT count(*)::text FROM users
     WHERE id = '11111111-1111-1111-1111-111111111111')
ORDER BY entity;

\echo 'verify.sql: all invariants held'
