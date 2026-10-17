package com.minidoodle.calendar.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a {@link Meeting}. A dedicated value object prevents mixing it up with {@link
 * SlotId}, {@link CalendarId}, or {@link UserId}.
 */
public record MeetingId(UUID value) {

    public MeetingId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static MeetingId of(UUID value) {
        return new MeetingId(value);
    }

    public static MeetingId random() {
        return new MeetingId(UUID.randomUUID());
    }
}
