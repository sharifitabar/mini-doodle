package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.application.port.out.MeetingRepository;
import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.Meeting;
import com.minidoodle.calendar.domain.model.Participant;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.jooq.tables.TimeSlots;
import com.minidoodle.support.PostgresIntegrationTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingRepositoryReadIT extends PostgresIntegrationTest {

    @Autowired private MeetingRepository meetings;

    @Test
    void findBySlotIdReturnsMeetingWithAllFieldsAndParticipantsInOrder() {
        Caller caller = createCaller();
        SlotId slotId = insertSlot(caller.calendarId());
        Meeting saved =
                Meeting.create(
                        slotId,
                        "Design review",
                        "Review the new booking flow",
                        List.of(
                                new Participant("Ada", "ada@example.com"),
                                new Participant("Grace", null),
                                new Participant(null, "guest@example.com")),
                        OffsetDateTime.now(ZoneOffset.UTC));
        meetings.save(saved);

        Meeting reloaded = meetings.findBySlotId(slotId).orElseThrow();

        assertThat(reloaded.id()).isEqualTo(saved.id());
        assertThat(reloaded.slotId()).isEqualTo(saved.slotId());
        assertThat(reloaded.title()).isEqualTo("Design review");
        assertThat(reloaded.description()).isEqualTo("Review the new booking flow");
        assertThat(reloaded.participants())
                .containsExactly(
                        new Participant("Ada", "ada@example.com"),
                        new Participant("Grace", null),
                        new Participant(null, "guest@example.com"));
        assertThat(reloaded.createdAt().toInstant()).isEqualTo(saved.createdAt().toInstant());
        assertThat(reloaded.updatedAt().toInstant()).isEqualTo(saved.updatedAt().toInstant());
    }

    @Test
    void findBySlotIdReturnsEmptyForSlotWithoutMeeting() {
        Caller caller = createCaller();
        SlotId slotId = insertSlot(caller.calendarId());

        assertThat(meetings.findBySlotId(slotId)).isEmpty();
    }

    private SlotId insertSlot(CalendarId calendarId) {
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
                .values(
                        id,
                        calendarId.value(),
                        now.plusDays(1),
                        60,
                        SlotStatus.FREE.name(),
                        now,
                        now)
                .execute();
        return SlotId.of(id);
    }
}
