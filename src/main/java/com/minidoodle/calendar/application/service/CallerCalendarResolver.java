package com.minidoodle.calendar.application.service;

import com.minidoodle.calendar.application.port.out.CalendarRepository;
import com.minidoodle.calendar.domain.exception.ForbiddenAccessException;
import com.minidoodle.calendar.domain.model.Calendar;
import com.minidoodle.calendar.domain.model.UserId;

import org.springframework.stereotype.Component;

/**
 * Resolves the calendar owned by the calling user, treating an unknown caller as forbidden
 * (FR-016).
 */
@Component
class CallerCalendarResolver {

    private final CalendarRepository calendars;

    CallerCalendarResolver(CalendarRepository calendars) {
        this.calendars = calendars;
    }

    Calendar resolve(UserId caller) {
        return calendars
                .findByOwnerId(caller)
                .orElseThrow(
                        () -> new ForbiddenAccessException("Unknown caller: " + caller.value()));
    }
}
