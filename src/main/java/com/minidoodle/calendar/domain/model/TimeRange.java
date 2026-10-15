package com.minidoodle.calendar.domain.model;

import com.minidoodle.calendar.domain.exception.InvalidSlotException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Immutable half-open time range {@code [start, end)} with a fixed duration in minutes.
 * The start is normalized to UTC so that equality and persistence are offset-independent.
 */
public record TimeRange(OffsetDateTime start, int durationMinutes) {

    public static final int MAX_DURATION_MINUTES = 1440;

    public TimeRange {
        Objects.requireNonNull(start, "start must not be null");
        start = start.withOffsetSameInstant(ZoneOffset.UTC);
        if (durationMinutes <= 0) {
            throw new InvalidSlotException("durationMinutes must be positive");
        }
        if (durationMinutes > MAX_DURATION_MINUTES) {
            throw new InvalidSlotException("durationMinutes must not exceed " + MAX_DURATION_MINUTES);
        }
    }

    public OffsetDateTime end() {
        return start.plusMinutes(durationMinutes);
    }

    public boolean overlaps(TimeRange other) {
        return start.isBefore(other.end()) && other.start.isBefore(end());
    }
}
