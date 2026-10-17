package com.minidoodle.calendar.domain.exception;

/** The requested slot overlaps an existing slot in the same calendar. */
public class SlotOverlapException extends DomainException {

    public SlotOverlapException(String message) {
        super(message);
    }
}
