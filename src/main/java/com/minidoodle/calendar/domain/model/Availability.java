package com.minidoodle.calendar.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Result of an availability query: the requested {@link TimeFrame} together with the
 * ordered, disjoint intervals ({@code FREE} / {@code BUSY} / {@code UNALLOCATED}) that
 * together cover the frame exactly. Built by the {@code AvailabilityCalculator}.
 */
public record Availability(TimeFrame frame, List<AvailabilityInterval> intervals) {

    public Availability {
        Objects.requireNonNull(frame, "frame must not be null");
        intervals = List.copyOf(intervals);
    }
}
