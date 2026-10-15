package com.minidoodle.shared.config;

import com.minidoodle.jooq.tables.Calendars;
import com.minidoodle.jooq.tables.Users;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Seeds a demo user and calendar for local development and docker-compose so the API
 * can be exercised immediately. Disabled in tests. Idempotent.
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
class DevDataSeeder implements ApplicationRunner {

    static final UUID DEMO_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID DEMO_CALENDAR_ID = UUID.fromString("11111111-1111-1111-1111-111111111112");

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final DSLContext dsl;

    DevDataSeeder(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void run(ApplicationArguments args) {
        dsl.insertInto(Users.USERS)
                .columns(Users.USERS.ID, Users.USERS.DISPLAY_NAME)
                .values(DEMO_USER_ID, "Demo User")
                .onConflictDoNothing()
                .execute();

        dsl.insertInto(Calendars.CALENDARS)
                .columns(Calendars.CALENDARS.ID, Calendars.CALENDARS.USER_ID)
                .values(DEMO_CALENDAR_ID, DEMO_USER_ID)
                .onConflictDoNothing()
                .execute();

        log.info("Dev demo user ready: X-User-Id={} (calendarId={})", DEMO_USER_ID, DEMO_CALENDAR_ID);
    }
}
