package com.minidoodle.calendar.domain.exception;

/**
 * The slot does not exist or is not visible to the calling user.
 */
public class SlotNotFoundException extends DomainException {

    public SlotNotFoundException(String message) {
        super(message);
    }
}
