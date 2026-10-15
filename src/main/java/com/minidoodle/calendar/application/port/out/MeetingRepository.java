package com.minidoodle.calendar.application.port.out;

import com.minidoodle.calendar.domain.model.Meeting;
import com.minidoodle.calendar.domain.model.SlotId;

import java.util.Optional;

/**
 * Outbound port for persisting {@link Meeting} aggregates. Meetings are addressed
 * through their slot: a slot holds at most one meeting.
 */
public interface MeetingRepository {

    void save(Meeting meeting);

    Optional<Meeting> findBySlotId(SlotId slotId);

    boolean existsBySlotId(SlotId slotId);

    void deleteBySlotId(SlotId slotId);
}
