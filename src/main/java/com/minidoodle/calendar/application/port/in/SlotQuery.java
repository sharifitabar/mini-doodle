package com.minidoodle.calendar.application.port.in;

import com.minidoodle.calendar.domain.model.SlotStatus;

import java.time.OffsetDateTime;

/**
 * Read-side filter for listing a caller's slots. {@code from}/{@code to} bound a
 * half-open window {@code [from, to)}; a slot matches when it overlaps the window.
 */
public record SlotQuery(
        OffsetDateTime from,
        OffsetDateTime to,
        SlotStatus status,
        int limit,
        int offset) {

    public static final int MAX_LIMIT = 100;
    public static final int DEFAULT_LIMIT = 50;
}
