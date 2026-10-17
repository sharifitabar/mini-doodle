package com.minidoodle.shared.web;

import com.minidoodle.calendar.domain.exception.InvalidCallerIdException;

/** The required {@code X-User-Id} header is missing, blank, or not a valid UUID. */
public class MissingCallerIdException extends InvalidCallerIdException {

    public MissingCallerIdException() {
        super("The X-User-Id header is required");
    }
}
