package com.minidoodle.calendar.application.port.out;

import com.minidoodle.calendar.domain.event.CalendarEvent;

import java.util.List;

/**
 * The durable record of calendar events. The application service calls {@link #append} in the same
 * transaction as the state change, so a failure of one removes the other.
 */
public interface EventOutbox {

    /** Adds the events to the outbox. The caller owns the transaction. */
    void append(List<CalendarEvent> events);
}
