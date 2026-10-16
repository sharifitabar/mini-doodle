package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.application.port.out.AvailabilityRepository;
import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.SlotOccupancy;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.TimeFrame;
import com.minidoodle.calendar.domain.model.TimeRange;
import com.minidoodle.jooq.tables.Meetings;
import com.minidoodle.jooq.tables.TimeSlots;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only adapter that loads the slots overlapping a frame for one calendar, joined with
 * whether each slot holds a meeting. The single range join avoids an N+1 lookup, and the
 * {@code slot_range} predicate is served by the GiST index {@code ix_time_slots_range}.
 * Nothing is written.
 */
@Repository
class JooqAvailabilityRepository implements AvailabilityRepository {

    private final DSLContext dsl;

    JooqAvailabilityRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<SlotOccupancy> findOccupancies(CalendarId calendarId, TimeFrame frame) {
        Field<Boolean> holdsMeeting = DSL.field(Meetings.MEETINGS.ID.isNotNull()).as("holds_meeting");

        return dsl.select(
                        TimeSlots.TIME_SLOTS.START_AT,
                        TimeSlots.TIME_SLOTS.DURATION_MINUTES,
                        TimeSlots.TIME_SLOTS.STATUS,
                        holdsMeeting)
                .from(TimeSlots.TIME_SLOTS)
                .leftJoin(Meetings.MEETINGS)
                .on(Meetings.MEETINGS.SLOT_ID.eq(TimeSlots.TIME_SLOTS.ID))
                .where(TimeSlots.TIME_SLOTS.CALENDAR_ID.eq(calendarId.value())
                        .and(overlapsFrame(frame)))
                .orderBy(TimeSlots.TIME_SLOTS.START_AT.asc(), TimeSlots.TIME_SLOTS.ID.asc())
                .fetch(record -> new SlotOccupancy(
                        new TimeRange(
                                record.get(TimeSlots.TIME_SLOTS.START_AT),
                                record.get(TimeSlots.TIME_SLOTS.DURATION_MINUTES)),
                        SlotStatus.valueOf(record.get(TimeSlots.TIME_SLOTS.STATUS)),
                        Boolean.TRUE.equals(record.get(holdsMeeting))));
    }

    /**
     * Overlap predicate against the half-open frame {@code [from, to)}. Uses the generated
     * {@code slot_range} column so the GiST index can serve the predicate; jOOQ OSS has no
     * range type, so plain SQL is used.
     */
    private Condition overlapsFrame(TimeFrame frame) {
        return DSL.condition(
                "slot_range && tstzrange({0}::timestamptz, {1}::timestamptz, '[)')",
                frame.start(), frame.end());
    }
}
