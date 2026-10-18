package com.minidoodle.shared.config;

import com.minidoodle.calendar.domain.model.TimeRange;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Slot policy bound from {@code app.slot.*}. {@link #maxDurationMinutes()} may only lower the
 * absolute domain/DB ceiling ({@link TimeRange#MAX_DURATION_MINUTES}); raising it requires a
 * schema change.
 */
@Validated
@ConfigurationProperties(prefix = "app.slot")
public record SlotProperties(
        @Min(1) @Max(TimeRange.MAX_DURATION_MINUTES) int maxDurationMinutes) {}
