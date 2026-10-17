package com.minidoodle.calendar.domain.exception;

/**
 * Raised when meeting input violates a domain invariant (blank or over-length title, a participant
 * with neither name nor email). Surfaced to clients as {@code 400}.
 */
public class InvalidMeetingException extends DomainException {

    public InvalidMeetingException(String message) {
        super(message);
    }
}
