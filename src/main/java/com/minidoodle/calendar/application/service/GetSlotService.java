package com.minidoodle.calendar.application.service;

import com.minidoodle.calendar.application.port.in.GetSlotUseCase;
import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.exception.SlotNotFoundException;
import com.minidoodle.calendar.domain.model.Calendar;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.calendar.domain.model.UserId;
import org.springframework.stereotype.Service;

@Service
class GetSlotService implements GetSlotUseCase {

    private final CallerCalendarResolver calendarResolver;
    private final SlotRepository slots;

    GetSlotService(CallerCalendarResolver calendarResolver, SlotRepository slots) {
        this.calendarResolver = calendarResolver;
        this.slots = slots;
    }

    @Override
    public TimeSlot getSlot(UserId caller, SlotId slotId) {
        Calendar calendar = calendarResolver.resolve(caller);
        return slots.findByIdAndCalendarId(slotId, calendar.id())
                .orElseThrow(() -> new SlotNotFoundException("Slot not found: " + slotId.value()));
    }
}
