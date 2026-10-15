package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.calendar.domain.model.SlotStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.OffsetDateTime;

/**
 * Partial update of a slot: any subset of editable fields; omitted fields are unchanged.
 */
public record UpdateSlotRequest(
        SlotStatus status,
        @Future OffsetDateTime startAt,
        @Min(1) @Max(1440) Integer durationMinutes) {

    @AssertTrue(message = "at least one of status, startAt or durationMinutes must be provided")
    boolean isAnyFieldPresent() {
        return status != null || startAt != null || durationMinutes != null;
    }
}
