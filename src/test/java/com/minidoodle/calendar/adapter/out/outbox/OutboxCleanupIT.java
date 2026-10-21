package com.minidoodle.calendar.adapter.out.outbox;

import com.minidoodle.calendar.application.port.out.EventOutbox;
import com.minidoodle.calendar.domain.event.SlotCreated;
import com.minidoodle.jooq.tables.Calendars;
import com.minidoodle.jooq.tables.OutboxEvents;
import com.minidoodle.jooq.tables.Users;
import com.minidoodle.jooq.tables.records.OutboxEventsRecord;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Cleanup removes published rows older than the retention interval and keeps the other rows. */
@SpringBootTest
@ActiveProfiles("test")
class OutboxCleanupIT {

    @Autowired private EventOutbox outbox;
    @Autowired private OutboxRelay relay;
    @Autowired private DSLContext dsl;

    private UUID userId;
    private UUID calendarId;

    @BeforeEach
    void seedCalendar() {
        userId = UUID.randomUUID();
        calendarId = UUID.randomUUID();
        dsl.insertInto(Users.USERS)
                .columns(Users.USERS.ID, Users.USERS.DISPLAY_NAME)
                .values(userId, "Outbox Cleanup User")
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

    private UUID appendEvent() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID slotId = UUID.randomUUID();
        SlotCreated event =
                new SlotCreated(
                        UUID.randomUUID(), calendarId, slotId, now.plusDays(1), 30, "FREE", now);
        outbox.append(List.of(event));
        return event.eventId();
    }

    private void setState(UUID eventId, String state, OffsetDateTime publishedAt) {
        dsl.update(OutboxEvents.OUTBOX_EVENTS)
                .set(OutboxEvents.OUTBOX_EVENTS.STATE, state)
                .set(OutboxEvents.OUTBOX_EVENTS.PUBLISHED_AT, publishedAt)
                .where(OutboxEvents.OUTBOX_EVENTS.EVENT_ID.eq(eventId))
                .execute();
    }

    private OutboxEventsRecord row(UUID eventId) {
        return dsl.selectFrom(OutboxEvents.OUTBOX_EVENTS)
                .where(OutboxEvents.OUTBOX_EVENTS.EVENT_ID.eq(eventId))
                .fetchOne();
    }

    @Test
    void cleanupRemovesAnOldPublishedRow() {
        UUID old = appendEvent();
        setState(old, "PUBLISHED", OffsetDateTime.now(ZoneOffset.UTC).minusDays(8));

        int removed = relay.cleanupOnce();

        assertThat(removed).isGreaterThanOrEqualTo(1);
        assertThat(row(old)).isNull();
    }

    @Test
    void cleanupKeepsARecentPublishedRow() {
        UUID recent = appendEvent();
        setState(recent, "PUBLISHED", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));

        relay.cleanupOnce();

        assertThat(row(recent)).isNotNull();
    }

    @Test
    void cleanupKeepsAFailedRow() {
        UUID failed = appendEvent();
        dsl.update(OutboxEvents.OUTBOX_EVENTS)
                .set(OutboxEvents.OUTBOX_EVENTS.STATE, "FAILED")
                .set(OutboxEvents.OUTBOX_EVENTS.FAILED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .set(OutboxEvents.OUTBOX_EVENTS.ATTEMPTS, 5)
                .where(OutboxEvents.OUTBOX_EVENTS.EVENT_ID.eq(failed))
                .execute();

        relay.cleanupOnce();

        assertThat(row(failed)).isNotNull();
    }
}
