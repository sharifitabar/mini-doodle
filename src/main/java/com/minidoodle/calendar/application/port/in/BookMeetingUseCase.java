package com.minidoodle.calendar.application.port.in;

import com.minidoodle.calendar.domain.model.UserId;

public interface BookMeetingUseCase {

    MeetingView bookMeeting(UserId caller, BookMeetingCommand command);
}
