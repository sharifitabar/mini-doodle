package com.minidoodle.calendar.adapter.in.web;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * A participant in a booking request. At least one of {@code name}/{@code email} must be present
 * and non-blank; participants are free-form (not required to be existing users).
 */
public record ParticipantRequest(@Size(max = 100) String name, @Size(max = 254) String email) {

    @JsonIgnore
    @AssertTrue(message = "A participant must have a name or an email")
    public boolean isNameOrEmailPresent() {
        return StringUtils.hasText(name) || StringUtils.hasText(email);
    }
}
