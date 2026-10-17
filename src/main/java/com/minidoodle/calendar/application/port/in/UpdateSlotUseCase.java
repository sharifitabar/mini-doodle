package com.minidoodle.calendar.application.port.in;

import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.calendar.domain.model.UserId;

public interface UpdateSlotUseCase {

    TimeSlot updateSlot(UserId caller, SlotId slotId, UpdateSlotCommand command);
}
