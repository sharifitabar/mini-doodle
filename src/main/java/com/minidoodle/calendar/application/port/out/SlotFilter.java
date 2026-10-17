package com.minidoodle.calendar.application.port.out;

import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.SlotStatus;

import java.time.OffsetDateTime;

/**
 * Filter criteria for listing/counting slots. {@code from}/{@code to} are optional bounds of a
 * half-open window {@code [from, to)}; a slot matches when it overlaps that window. Results are
 * always scoped to a single {@code calendarId}.
 */
public record SlotFilter(
        CalendarId calendarId,
        OffsetDateTime from,
        OffsetDateTime to,
        SlotStatus status,
        int limit,
        int offset) {}
