package com.minidoodle.calendar.domain.model;

import com.minidoodle.calendar.domain.event.CalendarEvent;
import com.minidoodle.calendar.domain.event.MeetingBooked;
import com.minidoodle.calendar.domain.event.MeetingCancelled;
import com.minidoodle.calendar.domain.event.SlotCreated;
import com.minidoodle.calendar.domain.event.SlotDeleted;
import com.minidoodle.calendar.domain.event.SlotUpdated;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The aggregate records one event for each real transition, and no event for an equal value. */
class TimeSlotEventsTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-21T09:00:00Z");
    private static final CalendarId CALENDAR = CalendarId.of(UUID.randomUUID());

    private TimeSlot freshSlot() {
        TimeSlot slot = TimeSlot.create(CALENDAR, new TimeRange(NOW.plusDays(1), 60), NOW);
        slot.drainEvents();
        return slot;
    }

    private static List<Participant> people() {
        return List.of(new Participant("P0", "p0@example.com"));
    }

    @Test
    void createRecordsOneSlotCreatedEvent() {
        TimeSlot slot = TimeSlot.create(CALENDAR, new TimeRange(NOW.plusDays(1), 60), NOW);

        List<CalendarEvent> events = slot.drainEvents();

        assertThat(events).hasSize(1);
        SlotCreated created = (SlotCreated) events.get(0);
        assertThat(created.aggregateId()).isEqualTo(slot.id().value());
        assertThat(created.calendarId()).isEqualTo(CALENDAR.value());
        assertThat(created.startAt()).isEqualTo(NOW.plusDays(1));
        assertThat(created.durationMinutes()).isEqualTo(60);
        assertThat(created.status()).isEqualTo("FREE");
        assertThat(created.type()).isEqualTo("slot.created");
        assertThat(created.schemaVersion()).isEqualTo(1);
        assertThat(created.aggregateType()).isEqualTo("slot");
    }

    @Test
    void bookRecordsOneMeetingBookedEventWithTheParticipantCount() {
        TimeSlot slot = freshSlot();

        Meeting meeting = slot.book("Planning", "Details", people(), NOW);
        List<CalendarEvent> events = slot.drainEvents();

        assertThat(events).hasSize(1);
        MeetingBooked booked = (MeetingBooked) events.get(0);
        assertThat(booked.aggregateId()).isEqualTo(slot.id().value());
        assertThat(booked.meetingId()).isEqualTo(meeting.id().value());
        assertThat(booked.title()).isEqualTo("Planning");
        assertThat(booked.participantCount()).isEqualTo(1);
        assertThat(booked.type()).isEqualTo("meeting.booked");
    }

    @Test
    void cancelRecordsOneMeetingCancelledEvent() {
        TimeSlot slot = freshSlot();
        Meeting meeting = slot.book("Planning", "Details", people(), NOW);
        slot.drainEvents();

        slot.cancelMeeting(NOW.plusMinutes(1));

        List<CalendarEvent> events = slot.drainEvents();
        MeetingCancelled cancelled = (MeetingCancelled) events.get(0);
        assertThat(cancelled.meetingId()).isEqualTo(meeting.id().value());
        assertThat(cancelled.aggregateId()).isEqualTo(slot.id().value());
        assertThat(cancelled.type()).isEqualTo("meeting.cancelled");
    }

    @Test
    void changeStatusRecordsAnUpdateOnlyForARealChange() {
        TimeSlot slot = freshSlot();

        slot.changeStatus(SlotStatus.FREE, NOW);
        assertThat(slot.drainEvents()).isEmpty();

        slot.changeStatus(SlotStatus.BUSY, NOW.plusMinutes(1));

        SlotUpdated updated = (SlotUpdated) slot.drainEvents().get(0);
        assertThat(updated.changed()).containsExactly("status");
        assertThat(updated.status()).isEqualTo("BUSY");
    }

    @Test
    void changeTimeRecordsAnUpdateOnlyForARealChange() {
        TimeSlot slot = freshSlot();

        slot.changeTime(slot.timeRange(), NOW);
        assertThat(slot.drainEvents()).isEmpty();

        TimeRange moved = new TimeRange(NOW.plusDays(2), 45);
        slot.changeTime(moved, NOW.plusMinutes(1));

        SlotUpdated updated = (SlotUpdated) slot.drainEvents().get(0);
        assertThat(updated.changed()).containsExactly("start_at", "duration_minutes");
        assertThat(updated.startAt()).isEqualTo(moved.start());
        assertThat(updated.durationMinutes()).isEqualTo(45);
    }

    @Test
    void oneCommandThatChangesTimeAndStatusGivesOneUpdate() {
        TimeSlot slot = freshSlot();

        slot.changeTime(new TimeRange(NOW.plusDays(2), 45), NOW.plusMinutes(1));
        slot.changeStatus(SlotStatus.BUSY, NOW.plusMinutes(1));

        List<CalendarEvent> events = slot.drainEvents();
        assertThat(events).hasSize(1);
        SlotUpdated updated = (SlotUpdated) events.get(0);
        assertThat(updated.changed()).containsExactly("start_at", "duration_minutes", "status");
    }

    @Test
    void recordDeletionRecordsOneSlotDeletedEvent() {
        TimeSlot slot = freshSlot();

        slot.recordDeletion(NOW.plusMinutes(1));

        SlotDeleted deleted = (SlotDeleted) slot.drainEvents().get(0);
        assertThat(deleted.aggregateId()).isEqualTo(slot.id().value());
        assertThat(deleted.startAt()).isEqualTo(NOW.plusDays(1));
        assertThat(deleted.durationMinutes()).isEqualTo(60);
        assertThat(deleted.status()).isEqualTo("FREE");
        assertThat(deleted.type()).isEqualTo("slot.deleted");
    }

    @Test
    void drainEventsReturnsTheEventsOnce() {
        TimeSlot slot = TimeSlot.create(CALENDAR, new TimeRange(NOW.plusDays(1), 60), NOW);

        assertThat(slot.drainEvents()).hasSize(1);
        assertThat(slot.drainEvents()).isEmpty();
    }
}
