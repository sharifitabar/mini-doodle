package com.minidoodle.calendar.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A time slot left a calendar. The payload keeps the last known values of the slot. */
public record SlotDeleted(
        UUID eventId,
        UUID calendarId,
        UUID slotId,
        OffsetDateTime startAt,
        int durationMinutes,
        String status,
        OffsetDateTime occurredAt)
        implements CalendarEvent {

    public static final String TYPE = "slot.deleted";

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
