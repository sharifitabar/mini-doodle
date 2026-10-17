package com.minidoodle.calendar.application.port.in;

import java.time.OffsetDateTime;

public record CreateSlotCommand(OffsetDateTime startAt, int durationMinutes) {}
