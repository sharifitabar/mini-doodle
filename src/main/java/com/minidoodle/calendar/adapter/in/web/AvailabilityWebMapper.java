package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.calendar.domain.model.Availability;
import com.minidoodle.calendar.domain.model.AvailabilityInterval;

import java.util.List;

final class AvailabilityWebMapper {

    private AvailabilityWebMapper() {
    }

    static AvailabilityResponse toResponse(Availability availability) {
        List<AvailabilityIntervalResponse> intervals = availability.intervals().stream()
                .map(AvailabilityWebMapper::toResponse)
                .toList();
        return new AvailabilityResponse(availability.frame().start(), availability.frame().end(), intervals);
    }

    private static AvailabilityIntervalResponse toResponse(AvailabilityInterval interval) {
        return new AvailabilityIntervalResponse(interval.start(), interval.end(), interval.kind());
    }
}
