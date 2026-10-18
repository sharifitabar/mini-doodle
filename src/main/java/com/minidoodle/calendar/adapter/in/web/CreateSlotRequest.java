package com.minidoodle.calendar.adapter.in.web;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateSlotRequest(
        @NotNull @Future OffsetDateTime startAt, @Min(1) @MaxSlotDuration int durationMinutes) {}
