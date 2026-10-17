package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.model.TimeRange;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.support.PostgresIntegrationTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class SlotRepositoryDeleteIT extends PostgresIntegrationTest {

    @Autowired private SlotRepository slots;

    @Test
    void deletesSlotScopedToCalendar() {
        Caller caller = createCaller();
        TimeSlot slot = insertSlot(caller);

        slots.deleteByIdAndCalendarId(slot.id(), caller.calendarId());

        assertThat(slots.findByIdAndCalendarId(slot.id(), caller.calendarId())).isEmpty();
    }

    @Test
    void doesNotDeleteSlotFromAnotherCalendar() {
        Caller owner = createCaller();
        Caller other = createCaller();
        TimeSlot slot = insertSlot(owner);

        slots.deleteByIdAndCalendarId(slot.id(), other.calendarId());

        assertThat(slots.findByIdAndCalendarId(slot.id(), owner.calendarId())).isPresent();
    }

    private TimeSlot insertSlot(Caller caller) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TimeSlot slot =
                TimeSlot.create(
                        caller.calendarId(), new TimeRange(now.plusDays(1).withNano(0), 30), now);
        slots.insert(slot);
        return slot;
    }
}
