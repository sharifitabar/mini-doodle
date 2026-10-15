package com.minidoodle.calendar.application.port.in;

import com.minidoodle.calendar.domain.model.TimeSlot;

import java.util.List;

public record SlotPage(List<TimeSlot> items, int limit, int offset, long total) {
}
