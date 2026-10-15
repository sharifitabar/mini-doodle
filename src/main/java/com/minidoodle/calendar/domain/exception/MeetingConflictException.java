package com.minidoodle.calendar.domain.exception;

/**
 * Raised when a meeting operation conflicts with the slot's current state: booking a
 * slot that is not free (or already holds a meeting), or mutating/deleting a slot while
 * a meeting exists. Surfaced to clients as {@code 409}.
 */
public class MeetingConflictException extends DomainException {

    public MeetingConflictException(String message) {
        super(message);
    }
}
