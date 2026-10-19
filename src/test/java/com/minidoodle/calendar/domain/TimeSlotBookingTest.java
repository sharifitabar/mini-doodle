package com.minidoodle.calendar.domain;

import com.minidoodle.calendar.domain.exception.MeetingConflictException;
import com.minidoodle.calendar.domain.exception.MeetingNotFoundException;
import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.Meeting;
import com.minidoodle.calendar.domain.model.Participant;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.TimeRange;
import com.minidoodle.calendar.domain.model.TimeSlot;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeSlotBookingTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-01T09:00:00Z");
    private static final List<Participant> PEOPLE = List.of(new Participant("Ada", null));

    @Test
    void bookingOwnsMeetingAndBlocksEveryManualMutation() {
        TimeSlot slot = slot();
        Meeting meeting = slot.book("Planning", null, PEOPLE, NOW);
        assertThat(slot.requireMeeting()).isSameAs(meeting);
        assertThat(slot.status()).isEqualTo(SlotStatus.BUSY);
        assertThatThrownBy(() -> slot.book("Again", null, PEOPLE, NOW))
                .isInstanceOf(MeetingConflictException.class);
        assertThatThrownBy(() -> slot.changeTime(new TimeRange(NOW.plusDays(2), 30), NOW))
                .isInstanceOf(MeetingConflictException.class);
        assertThatThrownBy(() -> slot.changeStatus(SlotStatus.FREE, NOW))
                .isInstanceOf(MeetingConflictException.class);
        assertThatThrownBy(slot::ensureDeletable).isInstanceOf(MeetingConflictException.class);
    }

    @Test
    void manualBusySlotCannotBeBooked() {
        TimeSlot slot = slot();
        slot.changeStatus(SlotStatus.BUSY, NOW);
        assertThatThrownBy(() -> slot.book("Planning", null, PEOPLE, NOW))
                .isInstanceOf(MeetingConflictException.class);
        assertThat(slot.findMeeting()).isEmpty();
    }

    @Test
    void cancellationReleasesSlotForEditingAndRebooking() {
        TimeSlot slot = slot();
        Meeting first = slot.book("First", null, PEOPLE, NOW);
        slot.cancelMeeting(NOW.plusMinutes(1));
        assertThat(slot.findMeeting()).isEmpty();
        assertThat(slot.status()).isEqualTo(SlotStatus.FREE);
        assertThat(slot.updatedAt()).isEqualTo(NOW.plusMinutes(1));
        slot.ensureDeletable();
        slot.changeTime(new TimeRange(NOW.plusDays(2), 45), NOW);
        assertThat(slot.book("Second", null, PEOPLE, NOW).id()).isNotEqualTo(first.id());
    }

    @Test
    void missingMeetingCannotBeReadOrCancelled() {
        TimeSlot slot = slot();
        assertThatThrownBy(slot::requireMeeting).isInstanceOf(MeetingNotFoundException.class);
        assertThatThrownBy(() -> slot.cancelMeeting(NOW))
                .isInstanceOf(MeetingNotFoundException.class);
    }

    @Test
    void failedMeetingValidationLeavesSlotUnchanged() {
        TimeSlot slot = slot();
        assertThatThrownBy(() -> slot.book("", null, PEOPLE, NOW))
                .isInstanceOf(RuntimeException.class);
        assertThat(slot.findMeeting()).isEmpty();
        assertThat(slot.status()).isEqualTo(SlotStatus.FREE);
    }

    @Test
    void restoringRejectsMeetingFromAnotherSlot() {
        Meeting other = Meeting.create(SlotId.random(), "Other", null, PEOPLE, NOW);
        assertThatThrownBy(() -> TimeSlot.restore(slot(), other))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private TimeSlot slot() {
        return TimeSlot.create(CalendarId.random(), new TimeRange(NOW.plusDays(1), 60), NOW);
    }
}
