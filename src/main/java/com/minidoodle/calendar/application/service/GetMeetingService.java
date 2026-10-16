package com.minidoodle.calendar.application.service;

import com.minidoodle.calendar.application.port.in.GetMeetingUseCase;
import com.minidoodle.calendar.application.port.in.MeetingView;
import com.minidoodle.calendar.application.port.out.MeetingRepository;
import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.exception.MeetingNotFoundException;
import com.minidoodle.calendar.domain.exception.SlotNotFoundException;
import com.minidoodle.calendar.domain.model.Calendar;
import com.minidoodle.calendar.domain.model.Meeting;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.calendar.domain.model.UserId;
import org.springframework.stereotype.Service;

@Service
class GetMeetingService implements GetMeetingUseCase {

    private final CallerCalendarResolver calendarResolver;
    private final SlotRepository slots;
    private final MeetingRepository meetings;
    private final MeetingMetrics metrics;

    GetMeetingService(
            CallerCalendarResolver calendarResolver,
            SlotRepository slots,
            MeetingRepository meetings,
            MeetingMetrics metrics) {
        this.calendarResolver = calendarResolver;
        this.slots = slots;
        this.meetings = meetings;
        this.metrics = metrics;
    }

    /**
     * Reads the meeting on a slot owned by the caller. A slot that does not exist in the
     * caller's calendar and a slot without a meeting are both reported as not found.
     */
    @Override
    public MeetingView getMeeting(UserId caller, SlotId slotId) {
        return metrics.timeGet(() -> {
            Calendar calendar = calendarResolver.resolve(caller);
            TimeSlot slot = slots.findByIdAndCalendarId(slotId, calendar.id())
                    .orElseThrow(() -> new SlotNotFoundException("Slot not found: " + slotId.value()));
            Meeting meeting = meetings.findBySlotId(slotId)
                    .orElseThrow(() -> new MeetingNotFoundException("No meeting on slot " + slotId.value()));
            metrics.meetingViewed(slotId);
            return new MeetingView(meeting, slot.timeRange());
        });
    }
}
