package com.minidoodle.calendar.application.port.in;

import com.minidoodle.calendar.domain.model.SlotStatus;

import java.time.OffsetDateTime;

/**
 * Requested change to a slot, applied as a single atomic update. Any field may be {@code null},
 * meaning "keep the current value"; at least one is expected to be present.
 */
public record UpdateSlotCommand(
        SlotStatus status, OffsetDateTime startAt, Integer durationMinutes) {}
