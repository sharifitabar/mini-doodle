package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.calendar.domain.model.SlotStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SlotResponse(
        UUID id,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        int durationMinutes,
        SlotStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
