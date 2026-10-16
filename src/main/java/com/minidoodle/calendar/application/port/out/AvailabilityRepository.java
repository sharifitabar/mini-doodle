package com.minidoodle.calendar.application.port.out;

import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.SlotOccupancy;
import com.minidoodle.calendar.domain.model.TimeFrame;

import java.util.List;

/**
 * Read-only port that loads the caller's slot occupancies overlapping a frame, together
 * with whether each slot holds a meeting. Availability is derived from this data and
 * nothing is written.
 */
public interface AvailabilityRepository {

    List<SlotOccupancy> findOccupancies(CalendarId calendarId, TimeFrame frame);
}
