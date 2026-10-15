package com.minidoodle.calendar.application.port.in;

import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.calendar.domain.model.UserId;

public interface CreateSlotUseCase {

    TimeSlot createSlot(UserId caller, CreateSlotCommand command);
}
