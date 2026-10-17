package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.application.port.out.MeetingRepository;
import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.exception.MeetingConflictException;
import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.Meeting;
import com.minidoodle.calendar.domain.model.Participant;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.jooq.tables.MeetingParticipants;
import com.minidoodle.jooq.tables.TimeSlots;
import com.minidoodle.support.PostgresIntegrationTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeetingRepositoryIT extends PostgresIntegrationTest {

    @Autowired private MeetingRepository meetings;

    @Autowired private SlotRepository slots;

    @Test
    void findBySlotIdReturnsMeetingWithParticipantsInOrder() {
        Caller caller = createCaller();
        SlotId slotId = insertSlot(caller.calendarId(), SlotStatus.FREE);
        Meeting saved = meeting(slotId);
        meetings.save(saved);

        Optional<Meeting> found = meetings.findBySlotId(slotId);

        assertThat(found).isPresent();
        Meeting meeting = found.get();
        assertThat(meeting.id()).isEqualTo(saved.id());
        assertThat(meeting.title()).isEqualTo("Sprint planning");
        assertThat(meeting.description()).isEqualTo("Kick off");
        assertThat(meeting.participants())
                .containsExactly(
                        new Participant("Ada", "ada@example.com"),
                        new Participant("Grace", null),
                        new Participant(null, "guest@example.com"));
    }

    @Test
    void findBySlotIdReturnsEmptyWhenNoMeeting() {
        Caller caller = createCaller();
        SlotId slotId = insertSlot(caller.calendarId(), SlotStatus.FREE);

        assertThat(meetings.findBySlotId(slotId)).isEmpty();
        assertThat(meetings.existsBySlotId(slotId)).isFalse();
    }

    @Test
    void deleteBySlotIdRemovesMeetingAndCascadesParticipants() {
        Caller caller = createCaller();
        SlotId slotId = insertSlot(caller.calendarId(), SlotStatus.FREE);
        Meeting saved = meeting(slotId);
        meetings.save(saved);

        meetings.deleteBySlotId(slotId);

        assertThat(meetings.findBySlotId(slotId)).isEmpty();
        int remainingParticipants =
                dsl.fetchCount(
                        MeetingParticipants.MEETING_PARTICIPANTS.where(
                                MeetingParticipants.MEETING_PARTICIPANTS.MEETING_ID.eq(
                                        saved.id().value())));
        assertThat(remainingParticipants).isZero();
    }

    @Test
    void deletingSlotWithMeetingIsBlockedByForeignKey() {
        Caller caller = createCaller();
        SlotId slotId = insertSlot(caller.calendarId(), SlotStatus.FREE);
        meetings.save(meeting(slotId));

        assertThatThrownBy(() -> slots.deleteByIdAndCalendarId(slotId, caller.calendarId()))
                .isInstanceOf(MeetingConflictException.class);
    }

    private Meeting meeting(SlotId slotId) {
        return Meeting.create(
                slotId,
                "Sprint planning",
                "Kick off",
                List.of(
                        new Participant("Ada", "ada@example.com"),
                        new Participant("Grace", null),
                        new Participant(null, "guest@example.com")),
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private SlotId insertSlot(CalendarId calendarId, SlotStatus status) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(TimeSlots.TIME_SLOTS)
                .columns(
                        TimeSlots.TIME_SLOTS.ID,
                        TimeSlots.TIME_SLOTS.CALENDAR_ID,
                        TimeSlots.TIME_SLOTS.START_AT,
                        TimeSlots.TIME_SLOTS.DURATION_MINUTES,
                        TimeSlots.TIME_SLOTS.STATUS,
                        TimeSlots.TIME_SLOTS.CREATED_AT,
                        TimeSlots.TIME_SLOTS.UPDATED_AT)
                .values(id, calendarId.value(), now.plusDays(1), 60, status.name(), now, now)
                .execute();
        return SlotId.of(id);
    }
}
