package com.minidoodle.calendar.domain.exception;

/**
 * The caller is not allowed to act on the requested resource, or is unknown.
 */
public class ForbiddenAccessException extends DomainException {

    public ForbiddenAccessException(String message) {
        super(message);
    }
}
