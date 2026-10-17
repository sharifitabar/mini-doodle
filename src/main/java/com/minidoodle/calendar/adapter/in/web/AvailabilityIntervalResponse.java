package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.calendar.domain.model.AvailabilityKind;

import java.time.OffsetDateTime;

public record AvailabilityIntervalResponse(
        OffsetDateTime startAt, OffsetDateTime endAt, AvailabilityKind kind) {}
