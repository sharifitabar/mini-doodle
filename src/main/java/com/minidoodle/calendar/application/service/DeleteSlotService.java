package com.minidoodle.calendar.application.service;

import com.minidoodle.calendar.application.port.in.DeleteSlotUseCase;
import com.minidoodle.calendar.application.port.out.MeetingRepository;
import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.exception.MeetingConflictException;
import com.minidoodle.calendar.domain.exception.SlotNotFoundException;
import com.minidoodle.calendar.domain.model.Calendar;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.UserId;
import org.springframework.stereotype.Service;

@Service
class DeleteSlotService implements DeleteSlotUseCase {

    private final CallerCalendarResolver calendarResolver;
    private final SlotRepository slots;
    private final MeetingRepository meetings;
    private final SlotMetrics metrics;

    DeleteSlotService(
            CallerCalendarResolver calendarResolver,
            SlotRepository slots,
            MeetingRepository meetings,
            SlotMetrics metrics) {
        this.calendarResolver = calendarResolver;
        this.slots = slots;
        this.meetings = meetings;
        this.metrics = metrics;
    }

    @Override
    public void deleteSlot(UserId caller, SlotId slotId) {
        Calendar calendar = calendarResolver.resolve(caller);
        slots.findByIdAndCalendarId(slotId, calendar.id())
                .orElseThrow(() -> new SlotNotFoundException("Slot not found: " + slotId.value()));
        if (meetings.existsBySlotId(slotId)) {
            throw new MeetingConflictException("Slot has a meeting; cancel it before deleting the slot");
        }
        slots.deleteByIdAndCalendarId(slotId, calendar.id());
        metrics.slotDeleted(slotId);
    }
}
