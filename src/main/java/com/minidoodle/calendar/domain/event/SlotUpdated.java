package com.minidoodle.calendar.domain.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** An editable field or several editable fields of a time slot changed. */
public record SlotUpdated(
        UUID eventId,
        UUID calendarId,
        UUID slotId,
        OffsetDateTime startAt,
        int durationMinutes,
        String status,
        List<String> changed,
        OffsetDateTime occurredAt)
        implements CalendarEvent {

    public static final String TYPE = "slot.updated";

    public SlotUpdated {
        changed = List.copyOf(changed);
    }

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
