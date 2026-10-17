package com.minidoodle.shared.config;

import com.minidoodle.jooq.tables.Calendars;
import com.minidoodle.jooq.tables.Users;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the demo-data seeder, which is disabled by default ({@code app.seed-demo-data=false}) and
 * was therefore the only hand-written class in the codebase with no coverage at all.
 *
 * <p>Excluding it from coverage verification would have been a suppression; the seeder is real
 * behaviour that runs on startup in the local and docker-compose profiles, so it is exercised here
 * against the same throwaway PostgreSQL the rest of the suite uses. Enabling the property starts a
 * second application context, and the Testcontainers JDBC driver reuses the container it has
 * already started for the other tests.
 */
@SpringBootTest(properties = "app.seed-demo-data=true")
@ActiveProfiles("test")
class DevDataSeederIT {

    @Autowired private DSLContext dsl;
    @Autowired private DevDataSeeder seeder;
    @Autowired private ApplicationArguments applicationArguments;

    @Test
    void seedsTheDemoIdentityOnStartupAndIsIdempotent() throws Exception {
        // The runner has already run once, while the application context started.
        assertThat(demoUserCount()).isEqualTo(1);
        assertThat(demoCalendarCount()).isEqualTo(1);

        seeder.run(applicationArguments);

        assertThat(demoUserCount()).isEqualTo(1);
        assertThat(demoCalendarCount()).isEqualTo(1);
    }

    private int demoUserCount() {
        return dsl.fetchCount(Users.USERS, Users.USERS.ID.eq(DevDataSeeder.DEMO_USER_ID));
    }

    private int demoCalendarCount() {
        return dsl.fetchCount(
                Calendars.CALENDARS, Calendars.CALENDARS.ID.eq(DevDataSeeder.DEMO_CALENDAR_ID));
    }
}
