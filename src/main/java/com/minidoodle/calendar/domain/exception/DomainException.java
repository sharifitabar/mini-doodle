package com.minidoodle.calendar.domain.exception;

/**
 * Base type for expected, domain-level failures. Surfaced to clients as RFC 7807 problem details by
 * the web layer.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
