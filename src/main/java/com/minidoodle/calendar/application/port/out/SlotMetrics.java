package com.minidoodle.calendar.application.port.out;

import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.TimeSlot;

import java.util.function.Supplier;

/** Application observability port; implementations own instrumentation details. */
public interface SlotMetrics {
    void slotCreated(TimeSlot slot);

    void slotUpdated(TimeSlot slot);

    void slotDeleted(SlotId slotId);

    <T> T timeCreate(Supplier<T> operation);

    <T> T timeList(Supplier<T> operation);
}
