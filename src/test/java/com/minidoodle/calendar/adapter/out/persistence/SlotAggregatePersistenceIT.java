package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.application.port.out.SlotFilter;
import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.exception.MeetingConflictException;
import com.minidoodle.calendar.domain.model.Participant;
import com.minidoodle.calendar.domain.model.TimeRange;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.jooq.tables.MeetingParticipants;
import com.minidoodle.jooq.tables.Meetings;
import com.minidoodle.support.PostgresIntegrationTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlotAggregatePersistenceIT extends PostgresIntegrationTest {
    @Autowired private SlotRepository slots;

    @Test
    void completeAggregateRoundTripsAndCancellationRemovesChildren() {
        Caller caller = createCaller();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TimeSlot slot =
                TimeSlot.create(caller.calendarId(), new TimeRange(now.plusDays(1), 60), now);
        var meeting = slot.book("Planning", "Details", people(), now);
        slots.insert(slot);

        TimeSlot loaded =
                slots.findByIdAndCalendarIdForUpdate(slot.id(), caller.calendarId()).orElseThrow();
        assertThat(loaded.requireMeeting().id()).isEqualTo(meeting.id());
        assertThat(loaded.requireMeeting().participants()).containsExactlyElementsOf(people());
        assertThatThrownBy(() -> loaded.changeTime(new TimeRange(now.plusDays(2), 45), now))
                .isInstanceOf(MeetingConflictException.class);
        slots.update(loaded);
        assertThat(
                        dsl.fetchCount(
                                MeetingParticipants.MEETING_PARTICIPANTS,
                                MeetingParticipants.MEETING_PARTICIPANTS.MEETING_ID.eq(
                                        meeting.id().value())))
                .isEqualTo(2);

        loaded.cancelMeeting(now);
        slots.update(loaded);
        assertThat(
                        slots.findByIdAndCalendarId(slot.id(), caller.calendarId())
                                .orElseThrow()
                                .findMeeting())
                .isEmpty();
        assertThat(dsl.fetchCount(Meetings.MEETINGS, Meetings.MEETINGS.ID.eq(meeting.id().value())))
                .isZero();
        assertThat(
                        dsl.fetchCount(
                                MeetingParticipants.MEETING_PARTICIPANTS,
                                MeetingParticipants.MEETING_PARTICIPANTS.MEETING_ID.eq(
                                        meeting.id().value())))
                .isZero();
    }

    @Test
    void cancellingAndRebookingBeforeSavingReplacesTheChildEntity() {
        Caller caller = createCaller();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TimeSlot slot =
                TimeSlot.create(caller.calendarId(), new TimeRange(now.plusDays(1), 60), now);
        var original = slot.book("Original", null, people(), now);
        slots.insert(slot);
        TimeSlot loaded =
                slots.findByIdAndCalendarIdForUpdate(slot.id(), caller.calendarId()).orElseThrow();
        loaded.cancelMeeting(now);
        var replacement = loaded.book("Replacement", null, people(), now);
        slots.update(loaded);
        assertThat(
                        slots.findByIdAndCalendarId(slot.id(), caller.calendarId())
                                .orElseThrow()
                                .requireMeeting()
                                .id())
                .isEqualTo(replacement.id());
        assertThat(
                        dsl.fetchCount(
                                MeetingParticipants.MEETING_PARTICIPANTS,
                                MeetingParticipants.MEETING_PARTICIPANTS.MEETING_ID.eq(
                                        original.id().value())))
                .isZero();
    }

    @Test
    void listLoadsCompleteAggregatesIncludingOrderedParticipants() {
        Caller caller = createCaller();
        SlotFilter filter = new SlotFilter(caller.calendarId(), null, null, null, 100, 0);
        assertThat(slots.find(filter)).isEmpty();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TimeSlot first =
                TimeSlot.create(caller.calendarId(), new TimeRange(now.plusDays(1), 60), now);
        first.book("First", null, people(), now);
        slots.insert(first);
        TimeSlot second =
                TimeSlot.create(caller.calendarId(), new TimeRange(now.plusDays(2), 60), now);
        slots.insert(second);
        TimeSlot third =
                TimeSlot.create(caller.calendarId(), new TimeRange(now.plusDays(3), 60), now);
        third.book("Third", null, people(), now);
        slots.insert(third);
        var found = slots.find(filter);
        assertThat(found).hasSize(3);
        assertThat(found.get(0).requireMeeting().participants())
                .containsExactlyElementsOf(people());
        assertThat(found.get(1).findMeeting()).isEmpty();
        assertThat(found.get(2).requireMeeting().title()).isEqualTo("Third");
        assertThatThrownBy(found.get(2)::ensureDeletable)
                .isInstanceOf(MeetingConflictException.class);
    }

    private List<Participant> people() {
        return List.of(new Participant("Ada", "ada@example.com"), new Participant("Grace", null));
    }
}
