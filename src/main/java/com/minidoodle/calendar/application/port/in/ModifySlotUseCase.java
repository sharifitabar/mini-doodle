package com.minidoodle.calendar.application.port.in;

import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.calendar.domain.model.UserId;

public interface ModifySlotUseCase {

    TimeSlot modifySlot(UserId caller, SlotId slotId, ModifySlotCommand command);
}
