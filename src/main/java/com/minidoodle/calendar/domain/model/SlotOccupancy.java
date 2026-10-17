package com.minidoodle.calendar.domain.model;

import java.util.Objects;

/**
 * One of the caller's slots that overlaps a queried frame, with the facts needed to classify it. A
 * slot counts as busy when it is explicitly marked {@link SlotStatus#BUSY} or holds a meeting;
 * otherwise it counts as free.
 */
public record SlotOccupancy(TimeRange range, SlotStatus status, boolean holdsMeeting) {

    public SlotOccupancy {
        Objects.requireNonNull(range, "range must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public boolean isBusy() {
        return status == SlotStatus.BUSY || holdsMeeting;
    }
}
