package com.minidoodle.calendar.application.service;

import com.minidoodle.calendar.application.port.in.ChangeSlotStatusUseCase;
import com.minidoodle.calendar.application.port.in.CreateSlotCommand;
import com.minidoodle.calendar.application.port.in.CreateSlotUseCase;
import com.minidoodle.calendar.application.port.in.DeleteSlotUseCase;
import com.minidoodle.calendar.application.port.in.GetSlotUseCase;
import com.minidoodle.calendar.application.port.in.ListSlotsUseCase;
import com.minidoodle.calendar.application.port.in.ModifySlotCommand;
import com.minidoodle.calendar.application.port.in.ModifySlotUseCase;
import com.minidoodle.calendar.application.port.in.SlotPage;
import com.minidoodle.calendar.application.port.in.SlotQuery;
import com.minidoodle.calendar.application.port.out.MeetingRepository;
import com.minidoodle.calendar.application.port.out.SlotFilter;
import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.exception.MeetingConflictException;
import com.minidoodle.calendar.domain.exception.SlotNotFoundException;
import com.minidoodle.calendar.domain.model.Calendar;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.TimeRange;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.calendar.domain.model.UserId;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Implements the slot use cases. Each inbound port stays a separate interface so the web
 * adapter depends only on the operations it uses, while the implementations are grouped
 * here by the aggregate they act on.
 */
@Service
class SlotApplicationService implements
        CreateSlotUseCase,
        GetSlotUseCase,
        ListSlotsUseCase,
        ChangeSlotStatusUseCase,
        ModifySlotUseCase,
        DeleteSlotUseCase {

    private final CallerCalendarResolver calendarResolver;
    private final SlotRepository slots;
    private final MeetingRepository meetings;
    private final SlotMetrics metrics;
    private final Clock clock;

    SlotApplicationService(
            CallerCalendarResolver calendarResolver,
            SlotRepository slots,
            MeetingRepository meetings,
            SlotMetrics metrics,
            Clock clock) {
        this.calendarResolver = calendarResolver;
        this.slots = slots;
        this.meetings = meetings;
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

    @Override
    public TimeSlot getSlot(UserId caller, SlotId slotId) {
        Calendar calendar = calendarResolver.resolve(caller);
        return slots.findByIdAndCalendarId(slotId, calendar.id())
                .orElseThrow(() -> new SlotNotFoundException("Slot not found: " + slotId.value()));
    }

    @Override
    public SlotPage listSlots(UserId caller, SlotQuery query) {
        return metrics.timeList(() -> {
            Calendar calendar = calendarResolver.resolve(caller);
            SlotFilter filter = new SlotFilter(
                    calendar.id(), query.from(), query.to(), query.status(), query.limit(), query.offset());
            List<TimeSlot> items = slots.find(filter);
            long total = slots.count(filter);
            return new SlotPage(items, query.limit(), query.offset(), total);
        });
    }

    @Override
    public TimeSlot changeStatus(UserId caller, SlotId slotId, SlotStatus newStatus) {
        Calendar calendar = calendarResolver.resolve(caller);
        TimeSlot slot = slots.findByIdAndCalendarId(slotId, calendar.id())
                .orElseThrow(() -> new SlotNotFoundException("Slot not found: " + slotId.value()));
        if (meetings.existsBySlotId(slotId)) {
            throw new MeetingConflictException("Slot has a meeting; cancel it before changing its status");
        }
        slot.changeStatus(newStatus, OffsetDateTime.now(clock));
        slots.update(slot);
        metrics.slotUpdated(slot);
        return slot;
    }

    @Override
    public TimeSlot modifySlot(UserId caller, SlotId slotId, ModifySlotCommand command) {
        Calendar calendar = calendarResolver.resolve(caller);
        TimeSlot slot = slots.findByIdAndCalendarId(slotId, calendar.id())
                .orElseThrow(() -> new SlotNotFoundException("Slot not found: " + slotId.value()));
        if (meetings.existsBySlotId(slotId)) {
            throw new MeetingConflictException("Slot has a meeting; cancel it before modifying the slot");
        }
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
