package com.minidoodle.calendar.adapter.in.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Caps {@code durationMinutes} at the configured {@code app.slot.max-duration-minutes} value (never
 * above the absolute domain/DB ceiling of 1440).
 */
@Documented
@Constraint(validatedBy = MaxSlotDurationValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxSlotDuration {

    String message() default "durationMinutes must not exceed the configured maximum";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
