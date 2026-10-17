package com.minidoodle.calendar.adapter.in.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record BookMeetingRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @NotEmpty @Valid List<ParticipantRequest> participants) {}
