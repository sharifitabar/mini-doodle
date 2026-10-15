package com.minidoodle.calendar.adapter.in.web;

import java.util.List;

public record SlotPageResponse(List<SlotResponse> items, int limit, int offset, long total) {
}
