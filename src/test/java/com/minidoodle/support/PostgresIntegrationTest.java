package com.minidoodle.support;

import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.UserId;
import com.minidoodle.jooq.tables.Meetings;
import com.minidoodle.jooq.tables.TimeSlots;
import com.minidoodle.jooq.tables.Calendars;
import com.minidoodle.jooq.tables.Users;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Base class for integration tests. Boots the full application context against a
 * throwaway PostgreSQL 16 container (started lazily by the Testcontainers JDBC driver
 * configured in {@code application-test.yml}) with Flyway migrations applied.
 *
 * <p>Each test runs in a rolled-back transaction so tests stay isolated. Tests that need
 * committed data or genuine concurrency must not extend this class.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class PostgresIntegrationTest {

    @Autowired
    protected DSLContext dsl;

    /** Creates a fresh user + calendar and returns the caller identity. */
    protected Caller createCaller() {
        UUID userId = UUID.randomUUID();
        UUID calendarId = UUID.randomUUID();
        dsl.insertInto(Users.USERS)
                .columns(Users.USERS.ID, Users.USERS.DISPLAY_NAME)
                .values(userId, "Test User")
                .execute();
        dsl.insertInto(Calendars.CALENDARS)
                .columns(Calendars.CALENDARS.ID, Calendars.CALENDARS.USER_ID)
                .values(calendarId, userId)
                .execute();
        return new Caller(UserId.of(userId), CalendarId.of(calendarId));
    }

    /** Inserts a slot directly and returns its id. */
    protected UUID insertSlot(CalendarId calendarId, OffsetDateTime start, int durationMinutes, SlotStatus status) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(TimeSlots.TIME_SLOTS)
                .columns(
                        TimeSlots.TIME_SLOTS.ID,
                        TimeSlots.TIME_SLOTS.CALENDAR_ID,
                        TimeSlots.TIME_SLOTS.START_AT,
                        TimeSlots.TIME_SLOTS.DURATION_MINUTES,
                        TimeSlots.TIME_SLOTS.STATUS,
                        TimeSlots.TIME_SLOTS.CREATED_AT,
                        TimeSlots.TIME_SLOTS.UPDATED_AT)
                .values(id, calendarId.value(), start, durationMinutes, status.name(), now, now)
                .execute();
        return id;
    }

    /** Inserts a minimal meeting on the given slot. */
    protected void insertMeeting(UUID slotId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(Meetings.MEETINGS)
                .columns(
                        Meetings.MEETINGS.ID,
                        Meetings.MEETINGS.SLOT_ID,
                        Meetings.MEETINGS.TITLE,
                        Meetings.MEETINGS.CREATED_AT,
                        Meetings.MEETINGS.UPDATED_AT)
                .values(UUID.randomUUID(), slotId, "Meeting", now, now)
                .execute();
    }

    public record Caller(UserId userId, CalendarId calendarId) {
    }
}
