package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.domain.model.Calendar;
import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.TimeRange;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.calendar.domain.model.UserId;
import com.minidoodle.jooq.tables.records.CalendarsRecord;
import com.minidoodle.jooq.tables.records.TimeSlotsRecord;

final class PersistenceMapper {

    private PersistenceMapper() {
    }

    static TimeSlot toTimeSlot(TimeSlotsRecord record) {
        return TimeSlot.of(
                SlotId.of(record.getId()),
                CalendarId.of(record.getCalendarId()),
                new TimeRange(record.getStartAt(), record.getDurationMinutes()),
                SlotStatus.valueOf(record.getStatus()),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }

    static Calendar toCalendar(CalendarsRecord record) {
        return Calendar.of(
                CalendarId.of(record.getId()),
                UserId.of(record.getUserId()),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
