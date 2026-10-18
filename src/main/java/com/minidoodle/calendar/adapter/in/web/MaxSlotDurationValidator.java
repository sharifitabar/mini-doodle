package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.shared.config.SlotProperties;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class MaxSlotDurationValidator implements ConstraintValidator<MaxSlotDuration, Integer> {

    private final SlotProperties slotProperties;

    MaxSlotDurationValidator(SlotProperties slotProperties) {
        this.slotProperties = slotProperties;
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int max = slotProperties.maxDurationMinutes();
        if (value <= max) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("durationMinutes must not exceed " + max)
                .addConstraintViolation();
        return false;
    }
}
