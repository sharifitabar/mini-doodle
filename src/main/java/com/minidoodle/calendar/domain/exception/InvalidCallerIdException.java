package com.minidoodle.calendar.domain.exception;

/** The caller identity supplied by the request is missing or malformed. */
public class InvalidCallerIdException extends DomainException {

    public InvalidCallerIdException(String message) {
        super(message);
    }
}
