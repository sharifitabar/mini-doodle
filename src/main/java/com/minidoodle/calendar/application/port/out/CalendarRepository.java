package com.minidoodle.calendar.application.port.out;

import com.minidoodle.calendar.domain.model.Calendar;
import com.minidoodle.calendar.domain.model.UserId;

import java.util.Optional;

public interface CalendarRepository {

    Optional<Calendar> findByOwnerId(UserId ownerId);
}
