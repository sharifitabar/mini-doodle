package com.minidoodle.support;

import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.UserId;
import com.minidoodle.jooq.tables.Calendars;
import com.minidoodle.jooq.tables.Users;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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

    public record Caller(UserId userId, CalendarId calendarId) {
    }
}
