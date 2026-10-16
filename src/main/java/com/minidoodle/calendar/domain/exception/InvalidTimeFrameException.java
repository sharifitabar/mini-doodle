package com.minidoodle.calendar.domain.exception;

/**
 * Raised when a requested availability frame is invalid: a bound is missing, the end is
 * not after the start, or the span exceeds the allowed maximum. Surfaced to clients as
 * {@code 400}.
 */
public class InvalidTimeFrameException extends DomainException {

    public InvalidTimeFrameException(String message) {
        super(message);
    }
}
