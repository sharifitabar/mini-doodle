package com.minidoodle.calendar.domain.model;

import java.util.Objects;
import java.util.UUID;

public record SlotId(UUID value) {

    public SlotId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static SlotId of(UUID value) {
        return new SlotId(value);
    }

    public static SlotId random() {
        return new SlotId(UUID.randomUUID());
    }
}
