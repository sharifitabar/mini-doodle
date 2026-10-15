package com.minidoodle.shared.web;

import com.minidoodle.calendar.domain.exception.InvalidCallerIdException;

/**
 * The {@code X-User-Id} header is present but not a valid UUID.
 */
public class MalformedCallerIdException extends InvalidCallerIdException {

    public MalformedCallerIdException(String rawValue) {
        super("The X-User-Id header is not a valid UUID: " + rawValue);
    }
}
