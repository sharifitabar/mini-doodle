package com.minidoodle.calendar.application.port.out;

import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.TimeSlot;

import java.util.List;
import java.util.Optional;

public interface SlotRepository {

    void insert(TimeSlot slot);

    void update(TimeSlot slot);

    void deleteByIdAndCalendarId(SlotId slotId, CalendarId calendarId);

    Optional<TimeSlot> findByIdAndCalendarId(SlotId slotId, CalendarId calendarId);

    /**
     * Loads the slot owned by the given calendar, acquiring a row lock for update. Callers must use
     * this variant when performing state-changing operations that may race with booking or
     * cancellation (update/delete/book/cancel), so the slot's invariant checks observe a consistent
     * snapshot and the subsequent write does not overwrite a concurrent change.
     */
    default Optional<TimeSlot> findByIdAndCalendarIdForUpdate(
            SlotId slotId, CalendarId calendarId) {
        return findByIdAndCalendarId(slotId, calendarId);
    }

    List<TimeSlot> find(SlotFilter filter);

    long count(SlotFilter filter);
}
