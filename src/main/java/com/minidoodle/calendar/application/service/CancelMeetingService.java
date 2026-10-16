package com.minidoodle.calendar.application.service;

import com.minidoodle.calendar.application.port.in.CancelMeetingUseCase;
import com.minidoodle.calendar.application.port.out.MeetingRepository;
import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.exception.MeetingNotFoundException;
import com.minidoodle.calendar.domain.exception.SlotNotFoundException;
import com.minidoodle.calendar.domain.model.Calendar;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.calendar.domain.model.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
class CancelMeetingService implements CancelMeetingUseCase {

    private final CallerCalendarResolver calendarResolver;
    private final SlotRepository slots;
    private final MeetingRepository meetings;
    private final MeetingMetrics metrics;
    private final Clock clock;

    CancelMeetingService(
            CallerCalendarResolver calendarResolver,
            SlotRepository slots,
            MeetingRepository meetings,
            MeetingMetrics metrics,
            Clock clock) {
        this.calendarResolver = calendarResolver;
        this.slots = slots;
        this.meetings = meetings;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Cancels the meeting on a slot owned by the caller in a single transaction: the meeting
     * (and its participants, by cascade) is deleted and the slot is returned to FREE.
     */
    @Override
    @Transactional
    public void cancelMeeting(UserId caller, SlotId slotId) {
        metrics.timeCancel(() -> {
            Calendar calendar = calendarResolver.resolve(caller);
            TimeSlot slot = slots.findByIdAndCalendarId(slotId, calendar.id())
                    .orElseThrow(() -> new SlotNotFoundException("Slot not found: " + slotId.value()));
            if (!meetings.existsBySlotId(slotId)) {
                throw new MeetingNotFoundException("No meeting on slot " + slotId.value());
            }

            meetings.deleteBySlotId(slotId);
            slot.changeStatus(SlotStatus.FREE, OffsetDateTime.now(clock));
            slots.update(slot);

            metrics.meetingCancelled(slotId);
        });
    }
}
