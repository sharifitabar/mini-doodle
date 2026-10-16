package com.minidoodle.calendar.domain;

import com.minidoodle.calendar.domain.exception.InvalidMeetingException;
import com.minidoodle.calendar.domain.model.Participant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParticipantTest {

    @Test
    void allowsNameOnly() {
        Participant participant = new Participant("Ada Lovelace", null);

        assertThat(participant.name()).isEqualTo("Ada Lovelace");
        assertThat(participant.email()).isNull();
    }

    @Test
    void allowsEmailOnly() {
        Participant participant = new Participant(null, "guest@example.com");

        assertThat(participant.name()).isNull();
        assertThat(participant.email()).isEqualTo("guest@example.com");
    }

    @Test
    void allowsBothNameAndEmail() {
        Participant participant = new Participant("Ada", "ada@example.com");

        assertThat(participant.name()).isEqualTo("Ada");
        assertThat(participant.email()).isEqualTo("ada@example.com");
    }

    @Test
    void rejectsWhenNeitherNameNorEmailIsPresent() {
        assertThatThrownBy(() -> new Participant(null, null))
                .isInstanceOf(InvalidMeetingException.class)
                .hasMessageContaining("name or an email");
    }

    @Test
    void treatsBlankFieldsAsAbsent() {
        assertThatThrownBy(() -> new Participant("   ", ""))
                .isInstanceOf(InvalidMeetingException.class);
    }

    @Test
    void trimsAndBlanksOutEmptyFields() {
        Participant participant = new Participant("  Ada  ", "   ");

        assertThat(participant.name()).isEqualTo("Ada");
        assertThat(participant.email()).isNull();
    }

    @Test
    void rejectsOverLongName() {
        String tooLong = "n".repeat(Participant.MAX_NAME_LENGTH + 1);

        assertThatThrownBy(() -> new Participant(tooLong, null))
                .isInstanceOf(InvalidMeetingException.class)
                .hasMessageContaining(String.valueOf(Participant.MAX_NAME_LENGTH));
    }

    @Test
    void rejectsOverLongEmail() {
        String tooLong = "e".repeat(Participant.MAX_EMAIL_LENGTH + 1) + "@example.com";

        assertThatThrownBy(() -> new Participant(null, tooLong))
                .isInstanceOf(InvalidMeetingException.class)
                .hasMessageContaining(String.valueOf(Participant.MAX_EMAIL_LENGTH));
    }

    @Test
    void equalityIsByNormalizedNameAndEmail() {
        assertThat(new Participant("Ada", "ada@example.com"))
                .isEqualTo(new Participant("  Ada ", " ada@example.com "));
    }
}
