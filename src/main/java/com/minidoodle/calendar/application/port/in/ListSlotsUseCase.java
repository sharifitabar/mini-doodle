package com.minidoodle.calendar.application.port.in;

import com.minidoodle.calendar.domain.model.UserId;

public interface ListSlotsUseCase {

    SlotPage listSlots(UserId caller, SlotQuery query);
}
