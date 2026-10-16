package com.minidoodle.calendar.application.port.in;

import com.minidoodle.calendar.domain.model.Participant;
import com.minidoodle.calendar.domain.model.SlotId;

import java.util.List;

/**
 * Command to book a meeting on a slot. Participants are free-form (not required to be
 * existing users).
 */
public record BookMeetingCommand(
        SlotId slotId,
        String title,
        String description,
        List<Participant> participants) {
}
