package com.minidoodle.calendar.application.port.in;

import java.time.OffsetDateTime;

/**
 * Request for the caller's aggregated availability over the half-open frame {@code [from, to)}.
 */
public record AvailabilityQuery(OffsetDateTime from, OffsetDateTime to) {
}
