package com.minidoodle.calendar.application.port.in;

import java.time.OffsetDateTime;

/**
 * Requested change to a slot's time. Either field may be {@code null}, meaning "keep the current
 * value"; at least one is expected to be present.
 */
public record ModifySlotCommand(OffsetDateTime startAt, Integer durationMinutes) {
}
