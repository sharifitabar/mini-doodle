package com.minidoodle.calendar.domain;

import com.minidoodle.calendar.domain.exception.InvalidMeetingException;
import com.minidoodle.calendar.domain.model.Meeting;
import com.minidoodle.calendar.domain.model.Participant;
import com.minidoodle.calendar.domain.model.SlotId;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeetingTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-01T08:00:00Z");
    private static final SlotId SLOT = SlotId.random();
    private static final Participant ADA = new Participant("Ada", "ada@example.com");
    private static final List<Participant> PARTICIPANTS = List.of(ADA);

    @Test
    void createsWithDefaultsAndUtcTimestamps() {
        Meeting meeting = Meeting.create(SLOT, "Sprint planning", "Kick off", List.of(ADA), NOW);

        assertThat(meeting.id()).isNotNull();
        assertThat(meeting.slotId()).isEqualTo(SLOT);
        assertThat(meeting.title()).isEqualTo("Sprint planning");
        assertThat(meeting.description()).isEqualTo("Kick off");
        assertThat(meeting.participants()).containsExactly(ADA);
        assertThat(meeting.createdAt()).isEqualTo(NOW.withOffsetSameInstant(ZoneOffset.UTC));
        assertThat(meeting.updatedAt()).isEqualTo(meeting.createdAt());
    }

    @Test
    void trimsTitle() {
        assertThat(Meeting.create(SLOT, "  Title  ", null, List.of(ADA), NOW).title())
                .isEqualTo("Title");
    }

    @Test
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> Meeting.create(SLOT, "   ", null, List.of(ADA), NOW))
                .isInstanceOf(InvalidMeetingException.class)
                .hasMessageContaining("title");
    }

    @Test
    void rejectsNullTitle() {
        assertThatThrownBy(() -> Meeting.create(SLOT, null, null, List.of(ADA), NOW))
                .isInstanceOf(InvalidMeetingException.class);
    }

    @Test
    void rejectsOverLongTitle() {
        String tooLong = "t".repeat(Meeting.MAX_TITLE_LENGTH + 1);

        assertThatThrownBy(() -> Meeting.create(SLOT, tooLong, null, List.of(ADA), NOW))
                .isInstanceOf(InvalidMeetingException.class)
                .hasMessageContaining(String.valueOf(Meeting.MAX_TITLE_LENGTH));
    }

    @Test
    void rejectsNoParticipants() {
        assertThatThrownBy(() -> Meeting.create(SLOT, "Title", null, List.of(), NOW))
                .isInstanceOf(InvalidMeetingException.class)
                .hasMessageContaining("participant");
    }

    @Test
    void rejectsTooManyParticipants() {
        List<Participant> tooMany =
                java.util.stream.IntStream.rangeClosed(1, Meeting.MAX_PARTICIPANTS + 1)
                        .mapToObj(i -> new Participant("P" + i, null))
                        .toList();

        assertThatThrownBy(() -> Meeting.create(SLOT, "Title", null, tooMany, NOW))
                .isInstanceOf(InvalidMeetingException.class)
                .hasMessageContaining(String.valueOf(Meeting.MAX_PARTICIPANTS));
    }

    @Test
    void truncatesTimestampsToMicrosecondPrecision() {
        OffsetDateTime withNanos =
                OffsetDateTime.of(2026, 11, 1, 8, 0, 0, 123_456_789, ZoneOffset.UTC);

        Meeting meeting = Meeting.create(SLOT, "Title", null, List.of(ADA), withNanos);

        assertThat(meeting.createdAt()).isEqualTo(withNanos.truncatedTo(ChronoUnit.MICROS));
        assertThat(meeting.createdAt().getNano()).isEqualTo(123_456_000);
        assertThat(meeting.updatedAt()).isEqualTo(meeting.createdAt());
    }

    @Test
    void rejectsNullParticipantElement() {
        List<Participant> withNull = new java.util.ArrayList<>();
        withNull.add(ADA);
        withNull.add(null);

        assertThatThrownBy(() -> Meeting.create(SLOT, "Title", null, withNull, NOW))
                .isInstanceOf(InvalidMeetingException.class);
    }

    @Test
    void treatsBlankDescriptionAsAbsent() {
        Meeting meeting = Meeting.create(SLOT, "Title", "   ", List.of(ADA), NOW);

        assertThat(meeting.description()).isNull();
    }

    @Test
    void rejectsOverLongDescription() {
        String tooLong = "d".repeat(Meeting.MAX_DESCRIPTION_LENGTH + 1);

        assertThatThrownBy(() -> Meeting.create(SLOT, "Title", tooLong, List.of(ADA), NOW))
                .isInstanceOf(InvalidMeetingException.class)
                .hasMessageContaining(String.valueOf(Meeting.MAX_DESCRIPTION_LENGTH));
    }

    @Test
    void rejectsNullSlotId() {
        assertThatThrownBy(() -> Meeting.create(null, "Title", null, List.of(ADA), NOW))
                .isInstanceOf(NullPointerException.class);
    }
}
