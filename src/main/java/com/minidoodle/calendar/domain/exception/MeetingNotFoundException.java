package com.minidoodle.calendar.domain.exception;

/**
 * Raised when a slot holds no meeting but a meeting is expected. Surfaced to clients
 * as {@code 404} (existence is not disclosed for slots the caller does not own).
 */
public class MeetingNotFoundException extends DomainException {

    public MeetingNotFoundException(String message) {
        super(message);
    }
}
