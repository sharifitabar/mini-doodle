package com.minidoodle.calendar.application.service;

import com.minidoodle.calendar.application.port.in.BookMeetingCommand;
import com.minidoodle.calendar.application.port.in.BookMeetingUseCase;
import com.minidoodle.calendar.application.port.in.MeetingView;
import com.minidoodle.calendar.application.port.out.MeetingRepository;
import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.exception.MeetingConflictException;
import com.minidoodle.calendar.domain.exception.SlotNotFoundException;
import com.minidoodle.calendar.domain.model.Calendar;
import com.minidoodle.calendar.domain.model.Meeting;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.calendar.domain.model.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
class BookMeetingService implements BookMeetingUseCase {

    private final CallerCalendarResolver calendarResolver;
    private final SlotRepository slots;
    private final MeetingRepository meetings;
    private final MeetingMetrics metrics;
    private final Clock clock;

    BookMeetingService(
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
     * Books a meeting in a single transaction: the slot must be owned by the caller and FREE,
     * the meeting is inserted (the {@code uq_meetings_slot} constraint is the authoritative
     * guard against double-booking), and the slot is marked BUSY.
     */
    @Override
    @Transactional
    public MeetingView bookMeeting(UserId caller, BookMeetingCommand command) {
        return metrics.timeBook(() -> {
            Calendar calendar = calendarResolver.resolve(caller);
            TimeSlot slot = slots.findByIdAndCalendarId(command.slotId(), calendar.id())
                    .orElseThrow(() -> new SlotNotFoundException("Slot not found: " + command.slotId().value()));
            if (slot.status() != SlotStatus.FREE) {
                throw new MeetingConflictException("Slot is not available for booking");
            }

            OffsetDateTime now = OffsetDateTime.now(clock);
            Meeting meeting = Meeting.create(
                    slot.id(), command.title(), command.description(), command.participants(), now);
            meetings.save(meeting);

            slot.changeStatus(SlotStatus.BUSY, now);
            slots.update(slot);

            metrics.meetingBooked(meeting, slot);
            return new MeetingView(meeting, slot.timeRange());
        });
    }
}
