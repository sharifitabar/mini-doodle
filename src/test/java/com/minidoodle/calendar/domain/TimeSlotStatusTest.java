package com.minidoodle.calendar.domain;

import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.TimeRange;
import com.minidoodle.calendar.domain.model.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TimeSlotStatusTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-01T08:00:00Z");

    @Test
    void defaultsToFree() {
        TimeSlot slot = newSlot();

        assertThat(slot.status()).isEqualTo(SlotStatus.FREE);
        assertThat(slot.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void togglesBetweenFreeAndBusy() {
        TimeSlot slot = newSlot();

        slot.changeStatus(SlotStatus.BUSY, NOW.plusMinutes(5));
        assertThat(slot.status()).isEqualTo(SlotStatus.BUSY);
        assertThat(slot.updatedAt()).isEqualTo(NOW.plusMinutes(5));

        slot.changeStatus(SlotStatus.FREE, NOW.plusMinutes(10));
        assertThat(slot.status()).isEqualTo(SlotStatus.FREE);
        assertThat(slot.updatedAt()).isEqualTo(NOW.plusMinutes(10));
    }

    @Test
    void settingTheSameStatusIsIdempotent() {
        TimeSlot slot = newSlot();
        slot.changeStatus(SlotStatus.BUSY, NOW.plusMinutes(5));
        OffsetDateTime afterFirstChange = slot.updatedAt();

        slot.changeStatus(SlotStatus.BUSY, NOW.plusMinutes(30));

        assertThat(slot.status()).isEqualTo(SlotStatus.BUSY);
        assertThat(slot.updatedAt()).isEqualTo(afterFirstChange);
    }

    @Test
    void normalizesUpdatedAtToUtc() {
        TimeSlot slot = newSlot();

        slot.changeStatus(SlotStatus.BUSY, OffsetDateTime.parse("2026-11-01T10:00:00+02:00"));

        assertThat(slot.updatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    private TimeSlot newSlot() {
        return TimeSlot.create(CalendarId.random(), new TimeRange(NOW.plusDays(1), 30), NOW);
    }
}
