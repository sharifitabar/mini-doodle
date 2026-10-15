package com.minidoodle.calendar.application.port.in;

import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.UserId;

public interface DeleteSlotUseCase {

    void deleteSlot(UserId caller, SlotId slotId);
}
