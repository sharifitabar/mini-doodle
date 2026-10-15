package com.minidoodle.calendar.domain.exception;

/**
 * The requested slot change violates a slot invariant (e.g. non-positive duration,
 * duration above the maximum, or a start instant in the past).
 */
public class InvalidSlotException extends DomainException {

    public InvalidSlotException(String message) {
        super(message);
    }
}
