package com.minidoodle.calendar.adapter.in.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MeetingResponse(
        UUID slotId,
        String title,
        String description,
        List<ParticipantResponse> participants,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
