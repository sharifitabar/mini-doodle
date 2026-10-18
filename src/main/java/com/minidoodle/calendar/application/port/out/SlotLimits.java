package com.minidoodle.calendar.application.port.out;

/**
 * Deployment policy for slot duration. The absolute domain/DB ceiling remains {@code
 * TimeRange.MAX_DURATION_MINUTES}; this port exposes the (possibly lower) configured maximum.
 */
public interface SlotLimits {

    int maxDurationMinutes();
}
