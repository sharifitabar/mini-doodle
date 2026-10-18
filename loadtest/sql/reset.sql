-- loadtest/sql/reset.sql — remove a previous load-test dataset so the next run starts clean.
--
--   docker compose --profile loadtest run --rm seed /sql/reset.sql
--
-- Two things make this file dangerous to get wrong, and both have bitten the design once:
--
-- 1. THE BOUNDARY. The demo user is `11111111-1111-1111-1111-111111111111`, which sits *inside*
--    the range "10000000-… to 20000000-…". Deleting by that range destroys the demo user. The
--    load-user block is the tight one below (`10000000-0000-4000-8000-*`), which excludes it and
--    still covers every generated id.
--
-- 2. THE ORDER. meetings.slot_id is ON DELETE RESTRICT, and the meetings created by a previous
--    run of the *manipulation flow* were written by the application with random UUIDs. A
--    namespace-scoped meeting delete would leave those rows behind and the user cascade would
--    then abort on a foreign-key violation — the reset would fail on exactly the runs that most
--    need it. So meetings are deleted by *slot ownership*, before the users.
--
-- Nothing outside the load-user block is touched: the demo user, hand-made data, and the
-- migration history all survive.

\set ON_ERROR_STOP on
\timing on

CREATE TEMP TABLE load_users AS
SELECT id FROM users
 WHERE id >= '10000000-0000-4000-8000-000000000000'
   AND id < '10000000-0000-4000-8001-000000000000';

BEGIN;

DO $$
DECLARE
    v_meetings  bigint;
    v_users     bigint;
    v_calendars bigint;
    v_slots     bigint;
    v_removed   bigint;
BEGIN
    SELECT count(*) INTO v_users FROM load_users;
    SELECT count(*) INTO v_calendars
      FROM calendars WHERE user_id IN (SELECT id FROM load_users);
    SELECT count(*) INTO v_slots
      FROM time_slots s
      JOIN calendars k ON k.id = s.calendar_id
     WHERE k.user_id IN (SELECT id FROM load_users);
    SELECT count(*) INTO v_meetings
      FROM meetings m
      JOIN time_slots s ON s.id = m.slot_id
      JOIN calendars k ON k.id = s.calendar_id
     WHERE k.user_id IN (SELECT id FROM load_users);

    -- (a) Meetings first, by slot ownership. Participants follow via ON DELETE CASCADE.
    DELETE FROM meetings m
     WHERE m.slot_id IN (
         SELECT s.id
           FROM time_slots s
           JOIN calendars k ON k.id = s.calendar_id
          WHERE k.user_id IN (SELECT id FROM load_users));
    GET DIAGNOSTICS v_removed = ROW_COUNT;
    RAISE NOTICE 'reset: % of % meetings deleted directly (rest went with their slots)',
        v_removed, v_meetings;

    -- (b) Then the users; ON DELETE CASCADE clears calendars, then slots.
    DELETE FROM users WHERE id IN (SELECT id FROM load_users);
    GET DIAGNOSTICS v_removed = ROW_COUNT;

    RAISE NOTICE 'reset: removed % users, % calendars, % slots, % meetings',
        v_users, v_calendars, v_slots, v_meetings;
END $$;

COMMIT;

-- Fresh statistics, so the next run does not plan against the dead tuples this reset just made.
ANALYZE users, calendars, time_slots, meetings, meeting_participants;

-- Post-conditions: the load block is empty, and everything outside it is untouched.
-- `load_users` outlives the delete (it is a temp table, not a view of the deleted rows), so it
-- still describes exactly the ids this reset was responsible for.
DO $$
DECLARE
    v_left_users bigint;
    v_left_slots bigint;
    v_left_meet  bigint;
    v_demo       bigint;
BEGIN
    SELECT count(*) INTO v_left_users FROM users WHERE id IN (SELECT id FROM load_users);
    SELECT count(*) INTO v_left_slots
      FROM time_slots s
      JOIN calendars k ON k.id = s.calendar_id
     WHERE k.user_id IN (SELECT id FROM load_users);
    SELECT count(*) INTO v_left_meet
      FROM meetings m
      JOIN time_slots s ON s.id = m.slot_id
      JOIN calendars k ON k.id = s.calendar_id
     WHERE k.user_id IN (SELECT id FROM load_users);

    IF v_left_users > 0 OR v_left_slots > 0 OR v_left_meet > 0 THEN
        RAISE EXCEPTION 'reset incomplete: % users, % slots, % meetings remain in the load block.',
            v_left_users, v_left_slots, v_left_meet;
    END IF;

    -- The demo user must have survived. It is the whole reason the block is tight, so this is
    -- asserted rather than reported: a reset that eats it is a bug, not a surprise.
    SELECT count(*) INTO v_demo FROM users WHERE id = '11111111-1111-1111-1111-111111111111';
    IF v_demo <> 1 THEN
        RAISE EXCEPTION 'reset destroyed the demo user (found % rows) — the load-user block is too wide.',
            v_demo;
    END IF;
    RAISE NOTICE 'reset ok: load block empty, demo user intact';
END $$;
