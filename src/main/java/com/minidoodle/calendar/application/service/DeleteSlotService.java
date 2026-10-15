package com.minidoodle.calendar.application.service;

import com.minidoodle.calendar.application.port.in.DeleteSlotUseCase;
import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.exception.SlotNotFoundException;
import com.minidoodle.calendar.domain.model.Calendar;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.UserId;
import org.springframework.stereotype.Service;

@Service
class DeleteSlotService implements DeleteSlotUseCase {

    private final CallerCalendarResolver calendarResolver;
    private final SlotRepository slots;
    private final SlotMetrics metrics;

    DeleteSlotService(CallerCalendarResolver calendarResolver, SlotRepository slots, SlotMetrics metrics) {
        this.calendarResolver = calendarResolver;
        this.slots = slots;
        this.metrics = metrics;
    }

    @Override
    public void deleteSlot(UserId caller, SlotId slotId) {
        Calendar calendar = calendarResolver.resolve(caller);
        slots.findByIdAndCalendarId(slotId, calendar.id())
                .orElseThrow(() -> new SlotNotFoundException("Slot not found: " + slotId.value()));
        slots.deleteByIdAndCalendarId(slotId, calendar.id());
        metrics.slotDeleted(slotId);
    }
}
