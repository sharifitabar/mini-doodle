package com.minidoodle.calendar.adapter.out.outbox;

import com.minidoodle.calendar.application.port.out.EventOutbox;
import com.minidoodle.calendar.application.port.out.EventPublisher;
import com.minidoodle.calendar.domain.event.CalendarEvent;
import com.minidoodle.calendar.domain.event.SlotCreated;
import com.minidoodle.jooq.tables.Calendars;
import com.minidoodle.jooq.tables.OutboxEvents;
import com.minidoodle.jooq.tables.Users;
import com.minidoodle.jooq.tables.records.OutboxEventsRecord;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import io.micrometer.core.instrument.MeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Delivery runs outside the write transaction, so this test does not extend {@code
 * PostgresIntegrationTest}. It commits its rows and removes them after each test.
 *
 * <p>The publisher is replaceable, so a failed delivery is a real code path and not a special
 * production branch.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(OutboxDeliveryIT.FailingPublisherConfig.class)
class OutboxDeliveryIT {

    private static final AtomicBoolean FAIL = new AtomicBoolean();
    private static final List<UUID> DELIVERED = new ArrayList<>();

    @TestConfiguration
    static class FailingPublisherConfig {

        @Bean
        @Primary
        EventPublisher testPublisher() {
            LoggingEventPublisher logging = new LoggingEventPublisher();
            return message -> {
                if (FAIL.get()) {
                    throw new IllegalStateException("publisher is not available");
                }
                DELIVERED.add(message.eventId());
                logging.publish(message);
            };
        }
    }

    @Autowired private EventOutbox outbox;
    @Autowired private OutboxRelay relay;
    @Autowired private DSLContext dsl;
    @Autowired private MeterRegistry registry;

    private UUID userId;
    private UUID calendarId;

    @BeforeEach
    void seedCalendar() {
        userId = UUID.randomUUID();
        calendarId = UUID.randomUUID();
        dsl.insertInto(Users.USERS)
                .columns(Users.USERS.ID, Users.USERS.DISPLAY_NAME)
                .values(userId, "Outbox Delivery User")
                .execute();
        dsl.insertInto(Calendars.CALENDARS)
                .columns(Calendars.CALENDARS.ID, Calendars.CALENDARS.USER_ID)
                .values(calendarId, userId)
                .execute();
        DELIVERED.clear();
        FAIL.set(false);
    }

    @AfterEach
    void cleanUp() {
        dsl.deleteFrom(OutboxEvents.OUTBOX_EVENTS)
                .where(OutboxEvents.OUTBOX_EVENTS.CALENDAR_ID.eq(calendarId))
                .execute();
        dsl.deleteFrom(Users.USERS).where(Users.USERS.ID.eq(userId)).execute();
        FAIL.set(false);
    }

    private UUID appendSlotCreated() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID slotId = UUID.randomUUID();
        CalendarEvent event =
                new SlotCreated(
                        UUID.randomUUID(), calendarId, slotId, now.plusDays(1), 30, "FREE", now);
        outbox.append(List.of(event));
        return event.eventId();
    }

    private OutboxEventsRecord row(UUID eventId) {
        return dsl.selectFrom(OutboxEvents.OUTBOX_EVENTS)
                .where(OutboxEvents.OUTBOX_EVENTS.EVENT_ID.eq(eventId))
                .fetchOne();
    }

    private long pendingRows() {
        return dsl.fetchCount(
                dsl.selectFrom(OutboxEvents.OUTBOX_EVENTS)
                        .where(OutboxEvents.OUTBOX_EVENTS.CALENDAR_ID.eq(calendarId))
                        .and(OutboxEvents.OUTBOX_EVENTS.STATE.eq("PENDING")));
    }

    @Test
    void pollDeliversPendingRowsInCaptureOrder() {
        UUID first = appendSlotCreated();
        UUID second = appendSlotCreated();

        relay.pollOnce();

        assertThat(row(first).getState()).isEqualTo("PUBLISHED");
        assertThat(row(first).getPublishedAt()).isNotNull();
        assertThat(row(second).getState()).isEqualTo("PUBLISHED");
        assertThat(DELIVERED).containsExactly(first, second);
    }

    @Test
    void aFailedDeliveryIncrementsTheAttemptAndKeepsTheRowPending() {
        UUID eventId = appendSlotCreated();
        FAIL.set(true);

        relay.pollOnce();

        OutboxEventsRecord row = row(eventId);
        assertThat(row.getState()).isEqualTo("PENDING");
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastError()).contains("publisher is not available");
        assertThat(DELIVERED).isEmpty();
    }

    @Test
    void fiveFailuresSetTheFailedStateAndStopTheAttempts() {
        UUID eventId = appendSlotCreated();
        FAIL.set(true);

        for (int attempt = 0; attempt < 5; attempt++) {
            relay.pollOnce();
        }

        OutboxEventsRecord row = row(eventId);
        assertThat(row.getState()).isEqualTo("FAILED");
        assertThat(row.getAttempts()).isEqualTo(5);
        assertThat(row.getFailedAt()).isNotNull();

        relay.pollOnce();

        assertThat(row(eventId).getAttempts()).isEqualTo(5);
    }

    @Test
    void runPollUpdatesThePendingGaugeAndTheCounters() {
        appendSlotCreated();
        appendSlotCreated();
        double publishedBefore = registry.get("minidoodle.outbox.published").counter().count();

        relay.runPoll();

        assertThat(pendingRows()).isZero();
        assertThat(registry.get("minidoodle.outbox.pending").gauge().value()).isZero();
        assertThat(registry.get("minidoodle.outbox.published").counter().count())
                .isEqualTo(publishedBefore + 2);
    }

    @Test
    void runPollCountsAFailedDeliveryAndShowsThePendingRow() {
        appendSlotCreated();
        double failedBefore = registry.get("minidoodle.outbox.failed").counter().count();
        FAIL.set(true);

        relay.runPoll();

        assertThat(pendingRows()).isEqualTo(1);
        assertThat(registry.get("minidoodle.outbox.pending").gauge().value()).isEqualTo(1);
        assertThat(registry.get("minidoodle.outbox.failed").counter().count())
                .isEqualTo(failedBefore + 1);
    }
}
