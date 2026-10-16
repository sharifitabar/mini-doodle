package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.application.port.out.MeetingRepository;
import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.Meeting;
import com.minidoodle.calendar.domain.model.Participant;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.jooq.tables.MeetingParticipants;
import com.minidoodle.jooq.tables.TimeSlots;
import com.minidoodle.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Cancelling a meeting deletes it, cascades its participants, and leaves the slot free to
 * be deleted again.
 */
class MeetingRepositoryCancelIT extends PostgresIntegrationTest {

    @Autowired
    private MeetingRepository meetings;

    @Autowired
    private SlotRepository slots;

    @Test
    void cancellingDeletesMeetingCascadesParticipantsAndFreesSlot() {
        Caller caller = createCaller();
        SlotId slotId = insertSlot(caller.calendarId(), SlotStatus.BUSY);
        Meeting meeting = Meeting.create(
                slotId,
                "Standup",
                null,
                List.of(new Participant("Ada", "ada@example.com"), new Participant("Grace", null)),
                OffsetDateTime.now(ZoneOffset.UTC));
        meetings.save(meeting);

        meetings.deleteBySlotId(slotId);

        assertThat(meetings.findBySlotId(slotId)).isEmpty();
        int remainingParticipants = dsl.fetchCount(MeetingParticipants.MEETING_PARTICIPANTS
                .where(MeetingParticipants.MEETING_PARTICIPANTS.MEETING_ID.eq(meeting.id().value())));
        assertThat(remainingParticipants).isZero();

        TimeSlot slot = slots.findByIdAndCalendarId(slotId, caller.calendarId()).orElseThrow();
        slot.changeStatus(SlotStatus.FREE, OffsetDateTime.now(ZoneOffset.UTC));
        slots.update(slot);

        assertThat(slots.findByIdAndCalendarId(slotId, caller.calendarId()).orElseThrow().status())
                .isEqualTo(SlotStatus.FREE);
        assertThatCode(() -> slots.deleteByIdAndCalendarId(slotId, caller.calendarId()))
                .doesNotThrowAnyException();
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
