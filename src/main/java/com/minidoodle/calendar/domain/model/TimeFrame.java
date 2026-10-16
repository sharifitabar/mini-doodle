package com.minidoodle.calendar.domain.model;

import com.minidoodle.calendar.domain.exception.InvalidTimeFrameException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Immutable half-open time frame {@code [start, end)} the availability query is computed
 * over. Both bounds are required, the end must be strictly after the start, and the span
 * must not exceed {@link #MAX_SPAN}. Bounds are normalized to UTC and truncated to
 * microsecond precision, matching {@link TimeRange}, so interval boundaries compare and
 * serialize identically to the slot data they are derived from.
 */
public record TimeFrame(OffsetDateTime start, OffsetDateTime end) {

    public static final Duration MAX_SPAN = Duration.ofDays(31);

    public TimeFrame {
        if (start == null || end == null) {
            throw new InvalidTimeFrameException("from and to are required");
        }
        start = start.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        end = end.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (!end.isAfter(start)) {
            throw new InvalidTimeFrameException("end must be after start");
        }
        if (Duration.between(start, end).compareTo(MAX_SPAN) > 0) {
            throw new InvalidTimeFrameException("frame must not exceed " + MAX_SPAN.toDays() + " days");
        }
    }
}
