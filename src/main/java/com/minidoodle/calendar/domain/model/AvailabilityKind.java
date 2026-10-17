package com.minidoodle.calendar.domain.model;

/**
 * Label of an availability interval: {@code FREE} (declared availability), {@code BUSY} (a slot
 * marked busy or holding a meeting), or {@code UNALLOCATED} (frame time covered by no slot).
 */
public enum AvailabilityKind {
    FREE,
    BUSY,
    UNALLOCATED
}
