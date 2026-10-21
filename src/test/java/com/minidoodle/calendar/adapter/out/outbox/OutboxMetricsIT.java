package com.minidoodle.calendar.adapter.out.outbox;

import com.minidoodle.calendar.application.port.out.OutboxMetrics;
import com.minidoodle.jooq.tables.Calendars;
import com.minidoodle.jooq.tables.OutboxEvents;
import com.minidoodle.jooq.tables.Users;
import com.minidoodle.jooq.tables.records.OutboxEventsRecord;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import io.micrometer.core.instrument.MeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/** The 3 meters report the 3 counts from the table. */
@SpringBootTest
@ActiveProfiles("test")
class OutboxMetricsIT {

    @Autowired private OutboxRelay relay;
    @Autowired private DSLContext dsl;
    @Autowired private MeterRegistry registry;
    @Autowired private OutboxMetrics metrics;

    private UUID userId;
    private UUID calendarId;

    @BeforeEach
    void seedCalendar() {
        userId = UUID.randomUUID();
        calendarId = UUID.randomUUID();
        dsl.insertInto(Users.USERS)
                .columns(Users.USERS.ID, Users.USERS.DISPLAY_NAME)
                .values(userId, "Outbox Metrics User")
                .execute();
        dsl.insertInto(Calendars.CALENDARS)
                .columns(Calendars.CALENDARS.ID, Calendars.CALENDARS.USER_ID)
                .values(calendarId, userId)
                .execute();
    }

    @AfterEach
    void cleanUp() {
        dsl.deleteFrom(OutboxEvents.OUTBOX_EVENTS)
                .where(OutboxEvents.OUTBOX_EVENTS.CALENDAR_ID.eq(calendarId))
                .execute();
        dsl.deleteFrom(Users.USERS).where(Users.USERS.ID.eq(userId)).execute();
    }

    private OutboxEventsRecord insertRow(String state) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID eventId = UUID.randomUUID();
        dsl.insertInto(OutboxEvents.OUTBOX_EVENTS)
                .columns(
                        OutboxEvents.OUTBOX_EVENTS.EVENT_ID,
                        OutboxEvents.OUTBOX_EVENTS.EVENT_TYPE,
                        OutboxEvents.OUTBOX_EVENTS.SCHEMA_VERSION,
                        OutboxEvents.OUTBOX_EVENTS.AGGREGATE_TYPE,
                        OutboxEvents.OUTBOX_EVENTS.AGGREGATE_ID,
                        OutboxEvents.OUTBOX_EVENTS.CALENDAR_ID,
                        OutboxEvents.OUTBOX_EVENTS.OCCURRED_AT,
                        OutboxEvents.OUTBOX_EVENTS.PAYLOAD,
                        OutboxEvents.OUTBOX_EVENTS.STATE,
                        OutboxEvents.OUTBOX_EVENTS.ATTEMPTS)
                .values(
                        eventId,
                        "slot.created",
                        1,
                        "slot",
                        UUID.randomUUID(),
                        calendarId,
                        now,
                        org.jooq.JSONB.valueOf("{\"status\":\"FREE\"}"),
                        state,
                        0)
                .execute();
        return dsl.selectFrom(OutboxEvents.OUTBOX_EVENTS)
                .where(OutboxEvents.OUTBOX_EVENTS.EVENT_ID.eq(eventId))
                .fetchOne();
    }

    @Test
    void thePendingGaugeShowsTheTableCountAfterAPoll() {
        insertRow("PENDING");
        insertRow("PENDING");
        insertRow("PUBLISHED");

        relay.runPoll();

        long pending =
                dsl.fetchCount(
                        dsl.selectFrom(OutboxEvents.OUTBOX_EVENTS)
                                .where(OutboxEvents.OUTBOX_EVENTS.STATE.eq("PENDING")));
        assertThat(registry.get("minidoodle.outbox.pending").gauge().value())
                .isEqualTo((double) pending);
    }

    @Test
    void theCountersCountDeliveredAndFailedRows() {
        insertRow("PENDING");
        double publishedBefore = registry.get("minidoodle.outbox.published").counter().count();
        double failedBefore = registry.get("minidoodle.outbox.failed").counter().count();

        relay.runPoll();

        assertThat(registry.get("minidoodle.outbox.published").counter().count())
                .isGreaterThanOrEqualTo(publishedBefore + 1);
        assertThat(registry.get("minidoodle.outbox.failed").counter().count())
                .isEqualTo(failedBefore);
    }

    @Test
    void thePortSetsThePendingValue() {
        metrics.pending(7);

        assertThat(registry.get("minidoodle.outbox.pending").gauge().value()).isEqualTo(7);

        metrics.pending(0);
    }
}
