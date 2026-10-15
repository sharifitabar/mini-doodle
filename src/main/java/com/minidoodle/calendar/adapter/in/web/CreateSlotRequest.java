package com.minidoodle.calendar.adapter.in.web;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record CreateSlotRequest(
        @NotNull @Future OffsetDateTime startAt,
        @Min(1) @Max(1440) int durationMinutes) {
}
