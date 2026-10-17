package com.minidoodle.calendar.domain.model;

import com.minidoodle.calendar.domain.exception.InvalidMeetingException;

/**
 * A meeting participant. Participants are free-form: they need not correspond to an existing user
 * account. Each participant carries a name and/or an email; at least one must be present and
 * non-blank. Blank values are normalized to {@code null}, so equality (by name + email) is
 * independent of surrounding whitespace.
 */
public record Participant(String name, String email) {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_EMAIL_LENGTH = 254;

    public Participant {
        name = normalize(name);
        email = normalize(email);
        if (name == null && email == null) {
            throw new InvalidMeetingException("A participant must have a name or an email");
        }
        if (name != null && name.length() > MAX_NAME_LENGTH) {
            throw new InvalidMeetingException(
                    "Participant name must not exceed " + MAX_NAME_LENGTH + " characters");
        }
        if (email != null && email.length() > MAX_EMAIL_LENGTH) {
            throw new InvalidMeetingException(
                    "Participant email must not exceed " + MAX_EMAIL_LENGTH + " characters");
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
