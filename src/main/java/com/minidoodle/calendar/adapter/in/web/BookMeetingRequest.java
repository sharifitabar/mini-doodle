package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.calendar.domain.model.Meeting;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record BookMeetingRequest(
        @NotBlank @Size(max = Meeting.MAX_TITLE_LENGTH) String title,
        @Size(max = Meeting.MAX_DESCRIPTION_LENGTH) String description,
        @NotEmpty @Size(max = Meeting.MAX_PARTICIPANTS) @Valid
                List<ParticipantRequest> participants) {}
