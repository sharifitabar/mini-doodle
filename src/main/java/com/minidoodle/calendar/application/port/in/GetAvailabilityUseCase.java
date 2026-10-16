package com.minidoodle.calendar.application.port.in;

import com.minidoodle.calendar.domain.model.Availability;
import com.minidoodle.calendar.domain.model.UserId;

public interface GetAvailabilityUseCase {

    Availability getAvailability(UserId caller, AvailabilityQuery query);
}
