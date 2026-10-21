package com.minidoodle.calendar.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A time slot became available in a calendar. */
public record SlotCreated(
        UUID eventId,
        UUID calendarId,
        UUID slotId,
        OffsetDateTime startAt,
        int durationMinutes,
        String status,
        OffsetDateTime occurredAt)
        implements CalendarEvent {

    public static final String TYPE = "slot.created";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public String aggregateType() {
        return "slot";
    }

    @Override
    public UUID aggregateId() {
        return slotId;
    }
}
