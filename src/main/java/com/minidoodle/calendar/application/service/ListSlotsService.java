package com.minidoodle.calendar.application.service;

import com.minidoodle.calendar.application.port.in.ListSlotsUseCase;
import com.minidoodle.calendar.application.port.in.SlotPage;
import com.minidoodle.calendar.application.port.in.SlotQuery;
import com.minidoodle.calendar.application.port.out.SlotFilter;
import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.model.Calendar;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.calendar.domain.model.UserId;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class ListSlotsService implements ListSlotsUseCase {

    private final CallerCalendarResolver calendarResolver;
    private final SlotRepository slots;

    ListSlotsService(CallerCalendarResolver calendarResolver, SlotRepository slots) {
        this.calendarResolver = calendarResolver;
        this.slots = slots;
    }

    @Override
    public SlotPage listSlots(UserId caller, SlotQuery query) {
        Calendar calendar = calendarResolver.resolve(caller);
        SlotFilter filter = new SlotFilter(
                calendar.id(), query.from(), query.to(), query.status(), query.limit(), query.offset());
        List<TimeSlot> items = slots.find(filter);
        long total = slots.count(filter);
        return new SlotPage(items, query.limit(), query.offset(), total);
    }
}
