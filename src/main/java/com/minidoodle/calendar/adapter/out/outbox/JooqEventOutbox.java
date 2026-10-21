package com.minidoodle.calendar.adapter.out.outbox;

import com.minidoodle.calendar.application.port.out.EventOutbox;
import com.minidoodle.calendar.application.port.out.OutboxMessage;
import com.minidoodle.calendar.domain.event.CalendarEvent;
import com.minidoodle.jooq.tables.OutboxEvents;
import com.minidoodle.jooq.tables.records.OutboxEventsRecord;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Stores calendar events in the outbox table. The caller's transaction is used, so the event row
 * and the state change commit or roll back together.
 *
 * <p>The envelope fields become columns and the changed facts become one {@code jsonb} document.
 * The meeting title stays in that document and never reaches the information-level log.
 */
@Component
class JooqEventOutbox implements EventOutbox {

    private final DSLContext dsl;
    private final EventPayload payload;

    JooqEventOutbox(DSLContext dsl, ObjectMapper json) {
        this.dsl = dsl;
        this.payload = new EventPayload(json);
    }

    @Override
    @Transactional
    public void append(List<CalendarEvent> events) {
        for (CalendarEvent event : events) {
            dsl.insertInto(OutboxEvents.OUTBOX_EVENTS)
                    .columns(
                            OutboxEvents.OUTBOX_EVENTS.EVENT_ID,
                            OutboxEvents.OUTBOX_EVENTS.EVENT_TYPE,
                            OutboxEvents.OUTBOX_EVENTS.SCHEMA_VERSION,
                            OutboxEvents.OUTBOX_EVENTS.AGGREGATE_TYPE,
                            OutboxEvents.OUTBOX_EVENTS.AGGREGATE_ID,
                            OutboxEvents.OUTBOX_EVENTS.CALENDAR_ID,
                            OutboxEvents.OUTBOX_EVENTS.OCCURRED_AT,
                            OutboxEvents.OUTBOX_EVENTS.PAYLOAD)
                    .values(
                            event.eventId(),
                            event.type(),
                            event.schemaVersion(),
                            event.aggregateType(),
                            event.aggregateId(),
                            event.calendarId(),
                            event.occurredAt(),
                            JSONB.valueOf(payload.toJson(event)))
                    .execute();
        }
    }

    /** Returns the pending rows in capture order, locked for this transaction. */
    @Transactional
    List<OutboxMessage> nextPending(int limit) {
        return dsl.selectFrom(OutboxEvents.OUTBOX_EVENTS)
                .where(OutboxEvents.OUTBOX_EVENTS.STATE.eq("PENDING"))
                .orderBy(OutboxEvents.OUTBOX_EVENTS.ID.asc())
                .limit(limit)
                .forUpdate()
                .skipLocked()
                .fetch(JooqEventOutbox::toMessage);
    }

    private static OutboxMessage toMessage(OutboxEventsRecord row) {
        return new OutboxMessage(
                row.getId(),
                row.getEventId(),
                row.getEventType(),
                row.getSchemaVersion(),
                row.getAggregateType(),
                row.getAggregateId(),
                row.getCalendarId(),
                row.getOccurredAt(),
                row.getPayload().data(),
                row.getAttempts());
    }

    @Transactional
    void markPublished(long id) {
        dsl.update(OutboxEvents.OUTBOX_EVENTS)
                .set(OutboxEvents.OUTBOX_EVENTS.STATE, "PUBLISHED")
                .set(OutboxEvents.OUTBOX_EVENTS.PUBLISHED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .where(
                        OutboxEvents.OUTBOX_EVENTS
                                .ID
                                .eq(id)
                                .and(OutboxEvents.OUTBOX_EVENTS.STATE.eq("PENDING")))
                .execute();
    }

    /** Counts one failed attempt, and moves the row to the failed state at the limit. */
    @Transactional
    void markAttemptFailed(long id, String error, int maxAttempts) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.update(OutboxEvents.OUTBOX_EVENTS)
                .set(
                        OutboxEvents.OUTBOX_EVENTS.ATTEMPTS,
                        OutboxEvents.OUTBOX_EVENTS.ATTEMPTS.plus(1))
                .set(OutboxEvents.OUTBOX_EVENTS.LAST_ERROR, error)
                .set(
                        OutboxEvents.OUTBOX_EVENTS.STATE,
                        org.jooq
                                .impl
                                .DSL
                                .when(
                                        OutboxEvents.OUTBOX_EVENTS.ATTEMPTS.plus(1).ge(maxAttempts),
                                        "FAILED")
                                .otherwise("PENDING"))
                .set(
                        OutboxEvents.OUTBOX_EVENTS.FAILED_AT,
                        org.jooq
                                .impl
                                .DSL
                                .when(
                                        OutboxEvents.OUTBOX_EVENTS.ATTEMPTS.plus(1).ge(maxAttempts),
                                        now)
                                .otherwise(OutboxEvents.OUTBOX_EVENTS.FAILED_AT))
                .where(OutboxEvents.OUTBOX_EVENTS.ID.eq(id))
                .execute();
    }

    long countPending() {
        return dsl.fetchCount(
                dsl.selectFrom(OutboxEvents.OUTBOX_EVENTS)
                        .where(OutboxEvents.OUTBOX_EVENTS.STATE.eq("PENDING")));
    }

    /** Removes published rows older than the threshold, in a bounded statement. */
    @Transactional
    int deletePublishedBefore(OffsetDateTime threshold) {
        List<Long> ids =
                dsl.select(OutboxEvents.OUTBOX_EVENTS.ID)
                        .from(OutboxEvents.OUTBOX_EVENTS)
                        .where(
                                OutboxEvents.OUTBOX_EVENTS
                                        .STATE
                                        .eq("PUBLISHED")
                                        .and(OutboxEvents.OUTBOX_EVENTS.PUBLISHED_AT.lt(threshold)))
                        .orderBy(OutboxEvents.OUTBOX_EVENTS.ID.asc())
                        .limit(1000)
                        .fetch(OutboxEvents.OUTBOX_EVENTS.ID);
        if (ids.isEmpty()) {
            return 0;
        }
        return dsl.deleteFrom(OutboxEvents.OUTBOX_EVENTS)
                .where(OutboxEvents.OUTBOX_EVENTS.ID.in(ids))
                .execute();
    }
}
