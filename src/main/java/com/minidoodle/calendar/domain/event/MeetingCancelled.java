package com.minidoodle.calendar.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/** The meeting of a time slot was cancelled, and the slot became free. */
public record MeetingCancelled(
        UUID eventId, UUID calendarId, UUID slotId, UUID meetingId, OffsetDateTime occurredAt)
        implements CalendarEvent {

    public static final String TYPE = "meeting.cancelled";

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
