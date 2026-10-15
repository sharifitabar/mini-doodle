package com.minidoodle.calendar.domain;

import com.minidoodle.calendar.domain.exception.InvalidSlotException;
import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.TimeRange;
import com.minidoodle.calendar.domain.model.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeSlotTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-01T08:00:00Z");
    private static final CalendarId CALENDAR = CalendarId.random();

    @Test
    void createsWithDefaultsAndTimestamps() {
        TimeRange range = new TimeRange(NOW.plusDays(1), 30);

        TimeSlot slot = TimeSlot.create(CALENDAR, range, NOW);

        assertThat(slot.id()).isNotNull();
        assertThat(slot.calendarId()).isEqualTo(CALENDAR);
        assertThat(slot.timeRange()).isEqualTo(range);
        assertThat(slot.status()).isEqualTo(SlotStatus.FREE);
        assertThat(slot.createdAt()).isEqualTo(NOW.withOffsetSameInstant(ZoneOffset.UTC));
        assertThat(slot.updatedAt()).isEqualTo(slot.createdAt());
    }

    @Test
    void rejectsNonPositiveDuration() {
        assertThatThrownBy(() -> new TimeRange(NOW.plusDays(1), 0))
                .isInstanceOf(InvalidSlotException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void rejectsDurationAboveMaximum() {
        assertThatThrownBy(() -> new TimeRange(NOW.plusDays(1), 1441))
                .isInstanceOf(InvalidSlotException.class)
                .hasMessageContaining("1440");
    }

    @Test
    void allowsMaximumDuration() {
        assertThat(new TimeRange(NOW.plusDays(1), 1440).durationMinutes()).isEqualTo(1440);
    }

    @Test
    void rejectsStartInThePast() {
        TimeRange past = new TimeRange(NOW.minusMinutes(1), 30);

        assertThatThrownBy(() -> TimeSlot.create(CALENDAR, past, NOW))
                .isInstanceOf(InvalidSlotException.class)
                .hasMessageContaining("future");
    }

    @Test
    void normalizesStartToUtc() {
        OffsetDateTime withOffset = OffsetDateTime.parse("2026-11-02T09:00:00+01:00");

        TimeSlot slot = TimeSlot.create(CALENDAR, new TimeRange(withOffset, 30), NOW);

        assertThat(slot.timeRange().start().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(slot.timeRange().start().toInstant()).isEqualTo(withOffset.toInstant());
    }
}
