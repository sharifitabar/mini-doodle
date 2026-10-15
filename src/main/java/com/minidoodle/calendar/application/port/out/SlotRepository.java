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

    List<TimeSlot> find(SlotFilter filter);

    long count(SlotFilter filter);
}
