package com.minidoodle.calendar.adapter.out.config;

import com.minidoodle.calendar.application.port.out.SlotLimits;
import com.minidoodle.shared.config.SlotProperties;

import org.springframework.stereotype.Component;

/** Outbound adapter: binds {@link SlotProperties} to the {@link SlotLimits} application port. */
@Component
class ConfiguredSlotLimits implements SlotLimits {

    private final SlotProperties properties;

    ConfiguredSlotLimits(SlotProperties properties) {
        this.properties = properties;
    }

    @Override
    public int maxDurationMinutes() {
        return properties.maxDurationMinutes();
    }
}
