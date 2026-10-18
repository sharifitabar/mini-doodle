package com.minidoodle.calendar.domain.service;

import com.minidoodle.calendar.domain.exception.MeetingConflictException;
import com.minidoodle.calendar.domain.exception.MeetingNotFoundException;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.TimeSlot;

/** Cross-aggregate rules evaluated with slot and meeting facts protected by the same lock. */
public final class BookingPolicy {
    private BookingPolicy() {}

    public static void requireBookable(TimeSlot slot) {
        if (slot.status() != SlotStatus.FREE) {
            throw new MeetingConflictException("Slot is not available for booking");
        }
    }

    public static void requireEditable(boolean holdsMeeting) {
        if (holdsMeeting) {
            throw new MeetingConflictException(
                    "Slot has a meeting; cancel it before updating the slot");
        }
    }

    public static void requireDeletable(boolean holdsMeeting) {
        if (holdsMeeting) {
            throw new MeetingConflictException(
                    "Slot has a meeting; cancel it before deleting the slot");
        }
    }

    public static void requireCancellable(SlotId slotId, boolean holdsMeeting) {
        if (!holdsMeeting) {
            throw new MeetingNotFoundException("No meeting on slot " + slotId.value());
        }
    }
}
