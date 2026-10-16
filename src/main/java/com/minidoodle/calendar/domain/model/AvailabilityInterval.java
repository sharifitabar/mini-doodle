package com.minidoodle.calendar.domain.model;

import com.minidoodle.calendar.domain.exception.InvalidTimeFrameException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * A labeled half-open interval {@code [start, end)} within a requested frame. Bounds are
 * normalized to UTC and truncated to microsecond precision; the end must be strictly after
 * the start.
 */
public record AvailabilityInterval(OffsetDateTime start, OffsetDateTime end, AvailabilityKind kind) {

    public AvailabilityInterval {
        if (start == null || end == null) {
            throw new InvalidTimeFrameException("interval bounds are required");
        }
        if (kind == null) {
            throw new InvalidTimeFrameException("interval kind is required");
        }
        start = start.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        end = end.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (!end.isAfter(start)) {
            throw new InvalidTimeFrameException("interval end must be after its start");
        }
    }
}
