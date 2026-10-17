package com.minidoodle.calendar.domain;

import com.minidoodle.calendar.domain.model.Availability;
import com.minidoodle.calendar.domain.model.AvailabilityInterval;
import com.minidoodle.calendar.domain.model.AvailabilityKind;
import com.minidoodle.calendar.domain.model.SlotOccupancy;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.TimeFrame;
import com.minidoodle.calendar.domain.model.TimeRange;
import com.minidoodle.calendar.domain.service.AvailabilityCalculator;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityCalculatorTest {

    private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-11-02T00:00:00Z");
    private static final AvailabilityCalculator CALCULATOR = new AvailabilityCalculator();

    @Test
    void emptyFrameYieldsSingleUnallocatedInterval() {
        Availability result = CALCULATOR.calculate(frame(4), List.of());

        assertThat(result.intervals())
                .containsExactly(interval(BASE, BASE.plusHours(4), AvailabilityKind.UNALLOCATED));
    }

    @Test
    void contiguousFreeSlotsMergeIntoOneWindow() {
        Availability result = CALCULATOR.calculate(frame(1), List.of(free(0, 30), free(30, 30)));

        assertThat(result.intervals())
                .containsExactly(interval(BASE, BASE.plusHours(1), AvailabilityKind.FREE));
    }

    @Test
    void nonAdjacentFreeSlotsStaySeparateWithUnallocatedGap() {
        Availability result = CALCULATOR.calculate(frame(2), List.of(free(0, 30), free(60, 30)));

        assertThat(result.intervals())
                .containsExactly(
                        interval(BASE, BASE.plusMinutes(30), AvailabilityKind.FREE),
                        interval(
                                BASE.plusMinutes(30),
                                BASE.plusHours(1),
                                AvailabilityKind.UNALLOCATED),
                        interval(BASE.plusHours(1), BASE.plusMinutes(90), AvailabilityKind.FREE),
                        interval(
                                BASE.plusMinutes(90),
                                BASE.plusHours(2),
                                AvailabilityKind.UNALLOCATED));
    }

    @Test
    void freeSlotTouchingBusySlotIsNotMerged() {
        Availability result = CALCULATOR.calculate(frame(1), List.of(free(0, 30), busy(30, 30)));

        assertThat(result.intervals())
                .containsExactly(
                        interval(BASE, BASE.plusMinutes(30), AvailabilityKind.FREE),
                        interval(BASE.plusMinutes(30), BASE.plusHours(1), AvailabilityKind.BUSY));
    }

    @Test
    void contiguousBusySlotsMerge() {
        Availability result = CALCULATOR.calculate(frame(1), List.of(busy(0, 30), busy(30, 30)));

        assertThat(result.intervals())
                .containsExactly(interval(BASE, BASE.plusHours(1), AvailabilityKind.BUSY));
    }

    @Test
    void slotsMarkedBusyAndHoldingMeetingAreBusy() {
        Availability result = CALCULATOR.calculate(frame(1), List.of(busy(0, 30), meeting(30, 30)));

        assertThat(result.intervals())
                .containsExactly(interval(BASE, BASE.plusHours(1), AvailabilityKind.BUSY));
    }

    @Test
    void slotExtendingBeyondFrameIsClipped() {
        Availability result = CALCULATOR.calculate(frame(2), List.of(free(0, 90)));

        assertThat(result.intervals())
                .containsExactly(
                        interval(BASE, BASE.plusMinutes(90), AvailabilityKind.FREE),
                        interval(
                                BASE.plusMinutes(90),
                                BASE.plusHours(2),
                                AvailabilityKind.UNALLOCATED));
    }

    @Test
    void slotTouchingFrameBoundaryIsStillIncluded() {
        // A slot starting before the frame and ending inside it must be clipped, not dropped.
        Availability result =
                CALCULATOR.calculate(
                        frame(2),
                        List.of(
                                new SlotOccupancy(
                                        new TimeRange(BASE.minusMinutes(30), 60),
                                        SlotStatus.FREE,
                                        false)));

        assertThat(result.intervals())
                .containsExactly(
                        interval(BASE, BASE.plusMinutes(30), AvailabilityKind.FREE),
                        interval(
                                BASE.plusMinutes(30),
                                BASE.plusHours(2),
                                AvailabilityKind.UNALLOCATED));
    }

    @Test
    void slotEntirelyOutsideFrameIsDropped() {
        Availability result =
                CALCULATOR.calculate(frame(2), List.of(free(300, 60), free(-120, 60)));

        assertThat(result.intervals())
                .containsExactly(interval(BASE, BASE.plusHours(2), AvailabilityKind.UNALLOCATED));
    }

    @Test
    void fullyCoveredFrameHasNoUnallocatedIntervals() {
        Availability result = CALCULATOR.calculate(frame(1), List.of(free(0, 30), meeting(30, 30)));

        assertThat(result.intervals())
                .containsExactly(
                        interval(BASE, BASE.plusMinutes(30), AvailabilityKind.FREE),
                        interval(BASE.plusMinutes(30), BASE.plusHours(1), AvailabilityKind.BUSY));
    }

    @Test
    void intervalsAreOrderedDisjointAndCoverTheFrameExactly() {
        TimeFrame frame = frame(4);
        Availability result =
                CALCULATOR.calculate(frame, List.of(free(0, 60), busy(60, 30), meeting(180, 30)));

        assertThat(result.frame()).isEqualTo(frame);
        assertThat(result.intervals())
                .containsExactly(
                        interval(BASE, BASE.plusHours(1), AvailabilityKind.FREE),
                        interval(BASE.plusHours(1), BASE.plusMinutes(90), AvailabilityKind.BUSY),
                        interval(
                                BASE.plusMinutes(90),
                                BASE.plusHours(3),
                                AvailabilityKind.UNALLOCATED),
                        interval(BASE.plusHours(3), BASE.plusMinutes(210), AvailabilityKind.BUSY),
                        interval(
                                BASE.plusMinutes(210),
                                BASE.plusHours(4),
                                AvailabilityKind.UNALLOCATED));

        OffsetDateTime cursor = frame.start();
        for (AvailabilityInterval interval : result.intervals()) {
            assertThat(interval.start()).isEqualTo(cursor);
            assertThat(interval.end()).isAfter(interval.start());
            cursor = interval.end();
        }
        assertThat(cursor).isEqualTo(frame.end());
    }

    private static TimeFrame frame(long hours) {
        return new TimeFrame(BASE, BASE.plusHours(hours));
    }

    private static SlotOccupancy free(long startMinutes, int durationMinutes) {
        return new SlotOccupancy(
                new TimeRange(BASE.plusMinutes(startMinutes), durationMinutes),
                SlotStatus.FREE,
                false);
    }

    private static SlotOccupancy busy(long startMinutes, int durationMinutes) {
        return new SlotOccupancy(
                new TimeRange(BASE.plusMinutes(startMinutes), durationMinutes),
                SlotStatus.BUSY,
                false);
    }

    private static SlotOccupancy meeting(long startMinutes, int durationMinutes) {
        return new SlotOccupancy(
                new TimeRange(BASE.plusMinutes(startMinutes), durationMinutes),
                SlotStatus.FREE,
                true);
    }

    private static AvailabilityInterval interval(
            OffsetDateTime start, OffsetDateTime end, AvailabilityKind kind) {
        return new AvailabilityInterval(start, end, kind);
    }
}
