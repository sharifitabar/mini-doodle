package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.calendar.application.port.in.CreateSlotCommand;
import com.minidoodle.calendar.application.port.in.SlotPage;
import com.minidoodle.calendar.domain.model.TimeSlot;

import java.util.List;

final class SlotWebMapper {

    private SlotWebMapper() {
    }

    static CreateSlotCommand toCommand(CreateSlotRequest request) {
        return new CreateSlotCommand(request.startAt(), request.durationMinutes());
    }

    static SlotResponse toResponse(TimeSlot slot) {
        return new SlotResponse(
                slot.id().value(),
                slot.timeRange().start(),
                slot.timeRange().end(),
                slot.timeRange().durationMinutes(),
                slot.status(),
                slot.createdAt(),
                slot.updatedAt());
    }

    static SlotPageResponse toResponse(SlotPage page) {
        List<SlotResponse> items = page.items().stream()
                .map(SlotWebMapper::toResponse)
                .toList();
        return new SlotPageResponse(items, page.limit(), page.offset(), page.total());
    }
}
