package com.minidoodle.calendar.domain.model;

import java.util.Objects;
import java.util.UUID;

public record CalendarId(UUID value) {

    public CalendarId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static CalendarId of(UUID value) {
        return new CalendarId(value);
    }

    public static CalendarId random() {
        return new CalendarId(UUID.randomUUID());
    }
}
