package com.minidoodle.calendar.application.service;

import com.minidoodle.calendar.application.port.in.CreateSlotCommand;
import com.minidoodle.calendar.application.port.in.CreateSlotUseCase;
import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.model.Calendar;
import com.minidoodle.calendar.domain.model.TimeRange;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.calendar.domain.model.UserId;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
class CreateSlotService implements CreateSlotUseCase {

    private final CallerCalendarResolver calendarResolver;
    private final SlotRepository slots;
    private final SlotMetrics metrics;
    private final Clock clock;

    CreateSlotService(
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
    public TimeSlot createSlot(UserId caller, CreateSlotCommand command) {
        return metrics.timeCreate(() -> {
            Calendar calendar = calendarResolver.resolve(caller);
            TimeRange timeRange = new TimeRange(command.startAt(), command.durationMinutes());
            TimeSlot slot = TimeSlot.create(calendar.id(), timeRange, OffsetDateTime.now(clock));
            slots.insert(slot);
            metrics.slotCreated(slot);
            return slot;
        });
    }
}
