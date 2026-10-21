package com.minidoodle.calendar.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A meeting was booked on a time slot. The payload has no participant name and no email. */
public record MeetingBooked(
        UUID eventId,
        UUID calendarId,
        UUID slotId,
        UUID meetingId,
        String title,
        int participantCount,
        OffsetDateTime occurredAt)
        implements CalendarEvent {

    public static final String TYPE = "meeting.booked";

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
