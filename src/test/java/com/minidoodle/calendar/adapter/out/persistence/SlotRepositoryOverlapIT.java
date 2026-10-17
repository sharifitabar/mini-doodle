package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.exception.SlotOverlapException;
import com.minidoodle.calendar.domain.model.TimeRange;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.support.PostgresIntegrationTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlotRepositoryOverlapIT extends PostgresIntegrationTest {

    @Autowired private SlotRepository slots;

    @Test
    void rejectsOverlappingInsertButAllowsAdjacent() {
        Caller caller = createCaller();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime start = now.plusDays(1).withNano(0);

        TimeSlot first = TimeSlot.create(caller.calendarId(), new TimeRange(start, 60), now);
        slots.insert(first);

        // Half-open [start, end): a slot starting exactly at the first slot's end is allowed.
        TimeSlot adjacent =
                TimeSlot.create(caller.calendarId(), new TimeRange(start.plusMinutes(60), 30), now);
        assertThatCode(() -> slots.insert(adjacent)).doesNotThrowAnyException();

        // Starting one minute before the first slot's end overlaps -> rejected by the constraint.
        TimeSlot overlapping =
                TimeSlot.create(caller.calendarId(), new TimeRange(start.plusMinutes(59), 30), now);
        assertThatThrownBy(() -> slots.insert(overlapping))
                .isInstanceOf(SlotOverlapException.class);
    }

    @Test
    void allowsSameRangeInDifferentCalendars() {
        Caller first = createCaller();
        Caller second = createCaller();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime start = now.plusDays(2).withNano(0);

        slots.insert(TimeSlot.create(first.calendarId(), new TimeRange(start, 60), now));

        assertThatCode(
                        () ->
                                slots.insert(
                                        TimeSlot.create(
                                                second.calendarId(),
                                                new TimeRange(start, 60),
                                                now)))
                .doesNotThrowAnyException();
    }
}
