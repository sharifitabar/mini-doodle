package com.minidoodle.calendar.application.port.in;

import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.UserId;

public interface CancelMeetingUseCase {

    void cancelMeeting(UserId caller, SlotId slotId);
}
