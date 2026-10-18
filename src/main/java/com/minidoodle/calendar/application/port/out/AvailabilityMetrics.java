package com.minidoodle.calendar.application.port.out;

import com.minidoodle.calendar.domain.model.Availability;

import java.util.function.Supplier;

/** Application observability port; implementations own instrumentation details. */
public interface AvailabilityMetrics {
    <T> T timeQuery(Supplier<T> operation);

    void recordQuery(Availability availability);
}
