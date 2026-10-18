package com.minidoodle.calendar.application.port.out;

import com.minidoodle.calendar.domain.model.Meeting;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.TimeSlot;

import java.util.function.Supplier;

/** Application observability port; implementations own instrumentation details. */
public interface MeetingMetrics {
    void meetingBooked(Meeting meeting, TimeSlot slot);

    void meetingViewed(SlotId slotId);

    void meetingCancelled(SlotId slotId);

    <T> T timeBook(Supplier<T> operation);

    <T> T timeGet(Supplier<T> operation);

    void timeCancel(Runnable operation);
}
