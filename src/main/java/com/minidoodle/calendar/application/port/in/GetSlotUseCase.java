package com.minidoodle.calendar.application.port.in;

import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.calendar.domain.model.UserId;

public interface GetSlotUseCase {

    TimeSlot getSlot(UserId caller, SlotId slotId);
}
