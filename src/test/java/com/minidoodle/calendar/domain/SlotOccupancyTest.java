package com.minidoodle.calendar.domain;

import com.minidoodle.calendar.domain.model.SlotOccupancy;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.TimeRange;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlotOccupancyTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-11-02T09:00:00Z");

    @Test
    void freeSlotWithoutMeetingIsNotBusy() {
        SlotOccupancy occupancy =
                new SlotOccupancy(new TimeRange(START, 60), SlotStatus.FREE, false);
        assertThat(occupancy.isBusy()).isFalse();
    }

    @Test
    void explicitlyBusySlotIsBusy() {
        SlotOccupancy occupancy =
                new SlotOccupancy(new TimeRange(START, 60), SlotStatus.BUSY, false);
        assertThat(occupancy.isBusy()).isTrue();
    }

    @Test
    void slotHoldingMeetingIsBusyEvenWhenMarkedFree() {
        SlotOccupancy occupancy =
                new SlotOccupancy(new TimeRange(START, 60), SlotStatus.FREE, true);
        assertThat(occupancy.isBusy()).isTrue();
    }
}
