package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.TimeRange;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.jooq.tables.Calendars;
import com.minidoodle.jooq.tables.Users;
import org.flywaydb.core.Flyway;
import org.jooq.CloseableDSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves slots are durably persisted: a slot written through one connection is still
 * readable through a brand-new connection (simulating a process/DataSource restart).
 */
class SlotRepositoryPersistenceIT {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void slotSurvivesConnectionRestart() {
        UUID userId = UUID.randomUUID();
        UUID calendarId = UUID.randomUUID();
        seedUserAndCalendar(userId, calendarId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TimeSlot created;
        try (CloseableDSLContext context = connect()) {
            SlotRepository repository = new JooqSlotRepository(context);
            created = TimeSlot.create(CalendarId.of(calendarId), new TimeRange(now.plusDays(1), 60), now);
            repository.insert(created);
        }

        try (CloseableDSLContext context = connect()) {
            SlotRepository repository = new JooqSlotRepository(context);
            Optional<TimeSlot> reloaded = repository.findByIdAndCalendarId(
                    created.id(), CalendarId.of(calendarId));

            assertThat(reloaded).isPresent();
            assertThat(reloaded.get().id()).isEqualTo(created.id());
            assertThat(reloaded.get().status()).isEqualTo(SlotStatus.FREE);
            assertThat(reloaded.get().timeRange().start().toInstant())
                    .isEqualTo(created.timeRange().start().toInstant());
            assertThat(reloaded.get().timeRange().durationMinutes()).isEqualTo(60);
        }
    }

    private CloseableDSLContext connect() {
        return DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void seedUserAndCalendar(UUID userId, UUID calendarId) {
        try (CloseableDSLContext context = connect()) {
            context.insertInto(Users.USERS)
                    .columns(Users.USERS.ID, Users.USERS.DISPLAY_NAME)
                    .values(userId, "Persistence User")
                    .execute();
            context.insertInto(Calendars.CALENDARS)
                    .columns(Calendars.CALENDARS.ID, Calendars.CALENDARS.USER_ID)
                    .values(calendarId, userId)
                    .execute();
        }
    }
}
