package com.minidoodle.calendar.application.port.in;

import com.minidoodle.calendar.domain.model.Meeting;
import com.minidoodle.calendar.domain.model.TimeRange;

/**
 * Read model returned by the meeting use cases: the meeting itself plus the time window
 * of the slot it belongs to (the meeting inherits its start/end from the slot).
 */
public record MeetingView(Meeting meeting, TimeRange slotTimeRange) {
}
