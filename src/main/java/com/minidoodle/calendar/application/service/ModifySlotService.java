package com.minidoodle.calendar.application.service;

import com.minidoodle.calendar.application.port.in.ModifySlotCommand;
import com.minidoodle.calendar.application.port.in.ModifySlotUseCase;
import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.exception.SlotNotFoundException;
import com.minidoodle.calendar.domain.model.Calendar;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.TimeRange;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.calendar.domain.model.UserId;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
class ModifySlotService implements ModifySlotUseCase {

    private final CallerCalendarResolver calendarResolver;
    private final SlotRepository slots;
    private final SlotMetrics metrics;
    private final Clock clock;

    ModifySlotService(
            CallerCalendarResolver calendarResolver,
            SlotRepository slots,
            SlotMetrics metrics,
            Clock clock) {
        this.calendarResolver = calendarResolver;
        this.slots = slots;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    public TimeSlot modifySlot(UserId caller, SlotId slotId, ModifySlotCommand command) {
        Calendar calendar = calendarResolver.resolve(caller);
        TimeSlot slot = slots.findByIdAndCalendarId(slotId, calendar.id())
                .orElseThrow(() -> new SlotNotFoundException("Slot not found: " + slotId.value()));

        TimeRange current = slot.timeRange();
        OffsetDateTime start = command.startAt() != null ? command.startAt() : current.start();
        int durationMinutes = command.durationMinutes() != null
                ? command.durationMinutes()
                : current.durationMinutes();

        slot.changeTime(new TimeRange(start, durationMinutes), OffsetDateTime.now(clock));
        slots.update(slot);
        metrics.slotUpdated(slot);
        return slot;
    }
}
