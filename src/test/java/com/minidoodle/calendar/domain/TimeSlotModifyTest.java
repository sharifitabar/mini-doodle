package com.minidoodle.calendar.domain;

import com.minidoodle.calendar.domain.exception.InvalidSlotException;
import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.TimeRange;
import com.minidoodle.calendar.domain.model.TimeSlot;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeSlotModifyTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-01T08:00:00Z");

    @Test
    void updatesTimeRangeAndTimestamp() {
        TimeSlot slot = newSlot();
        OffsetDateTime newStart = NOW.plusDays(2);

        slot.changeTime(new TimeRange(newStart, 90), NOW.plusMinutes(5));

        assertThat(slot.timeRange().start()).isEqualTo(newStart);
        assertThat(slot.timeRange().durationMinutes()).isEqualTo(90);
        assertThat(slot.timeRange().end()).isEqualTo(newStart.plusMinutes(90));
        assertThat(slot.updatedAt()).isEqualTo(NOW.plusMinutes(5));
    }

    @Test
    void keepsStatusWhenTimeChanges() {
        TimeSlot slot = newSlot();
        slot.changeStatus(SlotStatus.BUSY, NOW.plusMinutes(1));

        slot.changeTime(new TimeRange(NOW.plusDays(3), 30), NOW.plusMinutes(2));

        assertThat(slot.status()).isEqualTo(SlotStatus.BUSY);
    }

    @Test
    void rejectsStartInThePast() {
        TimeSlot slot = newSlot();

        assertThatThrownBy(() -> slot.changeTime(new TimeRange(NOW.minusMinutes(1), 30), NOW))
                .isInstanceOf(InvalidSlotException.class)
                .hasMessageContaining("future");
    }

    @Test
    void rejectsNonPositiveDuration() {
        TimeSlot slot = newSlot();

        assertThatThrownBy(() -> slot.changeTime(new TimeRange(NOW.plusDays(1), 0), NOW))
                .isInstanceOf(InvalidSlotException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void rejectsDurationBeyondMaximum() {
        TimeSlot slot = newSlot();

        assertThatThrownBy(() -> slot.changeTime(new TimeRange(NOW.plusDays(1), 1441), NOW))
                .isInstanceOf(InvalidSlotException.class)
                .hasMessageContaining("1440");
    }

    private TimeSlot newSlot() {
        return TimeSlot.create(CalendarId.random(), new TimeRange(NOW.plusDays(1), 30), NOW);
    }
}
