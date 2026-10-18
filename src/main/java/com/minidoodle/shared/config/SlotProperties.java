package com.minidoodle.shared.config;

import com.minidoodle.calendar.domain.model.TimeRange;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * YAML binding for {@code app.slot.*}. Exposed to the application only through the {@code
 * SlotLimits} outbound port (see {@code ConfiguredSlotLimits}). {@link #maxDurationMinutes()} may
 * only lower the absolute domain/DB ceiling ({@link TimeRange#MAX_DURATION_MINUTES}).
 */
@Validated
@ConfigurationProperties(prefix = "app.slot")
public record SlotProperties(@Min(1) @Max(TimeRange.MAX_DURATION_MINUTES) int maxDurationMinutes) {}
