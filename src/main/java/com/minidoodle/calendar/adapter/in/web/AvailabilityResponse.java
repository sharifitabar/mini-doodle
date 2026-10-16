package com.minidoodle.calendar.adapter.in.web;

import java.time.OffsetDateTime;
import java.util.List;

public record AvailabilityResponse(
        OffsetDateTime from,
        OffsetDateTime to,
        List<AvailabilityIntervalResponse> intervals) {
}
