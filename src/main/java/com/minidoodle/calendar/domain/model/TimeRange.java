package com.minidoodle.calendar.domain.model;

import com.minidoodle.calendar.domain.exception.InvalidSlotException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Immutable half-open time range {@code [start, end)} with a fixed duration in minutes. The start
 * is normalized to UTC and truncated to microsecond precision so that equality and persistence are
 * offset-independent and round-trip exactly (PostgreSQL timestamptz stores microseconds).
 */
public record TimeRange(OffsetDateTime start, int durationMinutes) {

    /**
     * Absolute ceiling matching {@code ck_duration_max} on {@code time_slots}. A deployment may
     * lower the allowed duration via configuration; raising it requires a schema change.
     */
    public static final int MAX_DURATION_MINUTES = 1440;

    public TimeRange {
        Objects.requireNonNull(start, "start must not be null");
        start = start.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (durationMinutes <= 0) {
            throw new InvalidSlotException("durationMinutes must be positive");
        }
        if (durationMinutes > MAX_DURATION_MINUTES) {
            throw new InvalidSlotException(
                    "durationMinutes must not exceed " + MAX_DURATION_MINUTES);
        }
    }

    public OffsetDateTime end() {
        return start.plusMinutes(durationMinutes);
    }
}
