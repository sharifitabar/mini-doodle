package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.application.port.out.AvailabilityRepository;
import com.minidoodle.calendar.domain.model.SlotOccupancy;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.TimeFrame;
import com.minidoodle.support.PostgresIntegrationTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityRepositoryIT extends PostgresIntegrationTest {

    @Autowired private AvailabilityRepository availability;

    private static final OffsetDateTime BASE =
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withNano(0);

    @Test
    void returnsOnlySlotsOverlappingTheFrameForTheCallersCalendar() {
        Caller caller = createCaller();
        insertSlot(caller.calendarId(), BASE, 60, SlotStatus.FREE);
        insertSlot(caller.calendarId(), BASE.plusDays(10), 60, SlotStatus.FREE);

        Caller other = createCaller();
        insertSlot(other.calendarId(), BASE, 60, SlotStatus.FREE);

        List<SlotOccupancy> found = availability.findOccupancies(caller.calendarId(), frame());

        assertThat(found).hasSize(1);
        assertThat(found.get(0).range().start()).isEqualTo(BASE);
        assertThat(found.get(0).range().durationMinutes()).isEqualTo(60);
        assertThat(found.get(0).holdsMeeting()).isFalse();
        assertThat(found.get(0).isBusy()).isFalse();
    }

    @Test
    void reportsWhetherEachSlotHoldsAMeeting() {
        Caller caller = createCaller();
        insertSlot(caller.calendarId(), BASE, 60, SlotStatus.BUSY);
        UUID withMeeting = insertSlot(caller.calendarId(), BASE.plusHours(2), 60, SlotStatus.FREE);
        insertMeeting(withMeeting);

        List<SlotOccupancy> found = availability.findOccupancies(caller.calendarId(), frame());

        assertThat(found).hasSize(2);
        assertThat(found).extracting(SlotOccupancy::isBusy).containsExactly(true, true);
        assertThat(found.get(0).holdsMeeting()).isFalse();
        assertThat(found.get(1).holdsMeeting()).isTrue();
        assertThat(found.get(0).range().start()).isEqualTo(BASE);
        assertThat(found.get(1).range().start()).isEqualTo(BASE.plusHours(2));
    }

    @Test
    void excludesSlotsThatOnlyTouchTheFrameBoundaries() {
        Caller caller = createCaller();
        insertSlot(caller.calendarId(), BASE.minusHours(1), 60, SlotStatus.FREE);
        insertSlot(caller.calendarId(), BASE.plusHours(4), 60, SlotStatus.FREE);
        insertSlot(caller.calendarId(), BASE.plusHours(1), 60, SlotStatus.BUSY);

        List<SlotOccupancy> found = availability.findOccupancies(caller.calendarId(), frame());

        assertThat(found).hasSize(1);
        assertThat(found.get(0).range().start()).isEqualTo(BASE.plusHours(1));
    }

    @Test
    void ordersSlotsByStart() {
        Caller caller = createCaller();
        insertSlot(caller.calendarId(), BASE.plusHours(2), 30, SlotStatus.FREE);
        insertSlot(caller.calendarId(), BASE, 30, SlotStatus.FREE);
        insertSlot(caller.calendarId(), BASE.plusHours(1), 30, SlotStatus.FREE);

        List<SlotOccupancy> found = availability.findOccupancies(caller.calendarId(), frame());

        assertThat(found)
                .extracting(o -> o.range().start())
                .containsExactly(BASE, BASE.plusHours(1), BASE.plusHours(2));
    }

    private static TimeFrame frame() {
        return new TimeFrame(BASE, BASE.plusHours(4));
    }
}
