package com.minidoodle.calendar.application.service;

import com.minidoodle.calendar.application.port.in.BookMeetingCommand;
import com.minidoodle.calendar.application.port.in.BookMeetingUseCase;
import com.minidoodle.calendar.application.port.in.CancelMeetingUseCase;
import com.minidoodle.calendar.application.port.in.GetMeetingUseCase;
import com.minidoodle.calendar.application.port.in.MeetingView;
import com.minidoodle.calendar.application.port.out.EventOutbox;
import com.minidoodle.calendar.application.port.out.MeetingMetrics;
import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.exception.SlotNotFoundException;
import com.minidoodle.calendar.domain.model.Calendar;
import com.minidoodle.calendar.domain.model.Meeting;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.calendar.domain.model.UserId;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the meeting use cases. Each inbound port stays a separate interface so the web adapter
 * depends only on the operations it uses, while the implementations are grouped here by the
 * aggregate they act on.
 */
@Service
class MeetingApplicationService
        implements BookMeetingUseCase, GetMeetingUseCase, CancelMeetingUseCase {

    private final CallerCalendarResolver calendarResolver;
    private final SlotRepository slots;
    private final EventOutbox outbox;
    private final MeetingMetrics metrics;
    private final Clock clock;

    MeetingApplicationService(
            CallerCalendarResolver calendarResolver,
            SlotRepository slots,
            EventOutbox outbox,
            MeetingMetrics metrics,
            Clock clock) {
        this.calendarResolver = calendarResolver;
        this.slots = slots;
        this.outbox = outbox;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Books a meeting in a single transaction: the slot must be owned by the caller and FREE, the
     * meeting is inserted (the {@code uq_meetings_slot} constraint is the authoritative guard
     * against double-booking), and the slot is marked BUSY.
     */
    @Override
    @Transactional
    public MeetingView bookMeeting(UserId caller, BookMeetingCommand command) {
        return metrics.timeBook(
                () -> {
                    Calendar calendar = calendarResolver.resolve(caller);
                    TimeSlot slot =
                            slots.findByIdAndCalendarIdForUpdate(command.slotId(), calendar.id())
                                    .orElseThrow(
                                            () ->
                                                    new SlotNotFoundException(
                                                            "Slot not found: "
                                                                    + command.slotId().value()));
                    Meeting meeting =
                            slot.book(
                                    command.title(),
                                    command.description(),
                                    command.participants(),
                                    OffsetDateTime.now(clock));
                    slots.update(slot);
                    outbox.append(slot.drainEvents());

                    metrics.meetingBooked(meeting, slot);
                    return new MeetingView(meeting, slot.timeRange());
                });
    }

    /**
     * Reads the meeting on a slot owned by the caller. A slot that does not exist in the caller's
     * calendar and a slot without a meeting are both reported as not found.
     */
    @Override
    public MeetingView getMeeting(UserId caller, SlotId slotId) {
        return metrics.timeGet(
                () -> {
                    Calendar calendar = calendarResolver.resolve(caller);
                    TimeSlot slot =
                            slots.findByIdAndCalendarId(slotId, calendar.id())
                                    .orElseThrow(
                                            () ->
                                                    new SlotNotFoundException(
                                                            "Slot not found: " + slotId.value()));
                    Meeting meeting = slot.requireMeeting();
                    metrics.meetingViewed(slotId);
                    return new MeetingView(meeting, slot.timeRange());
                });
    }

    /**
     * Cancels the meeting on a slot owned by the caller in a single transaction: the meeting (and
     * its participants, by cascade) is deleted and the slot is returned to FREE.
     */
    @Override
    @Transactional
    public void cancelMeeting(UserId caller, SlotId slotId) {
        metrics.timeCancel(
                () -> {
                    Calendar calendar = calendarResolver.resolve(caller);
                    TimeSlot slot =
                            slots.findByIdAndCalendarIdForUpdate(slotId, calendar.id())
                                    .orElseThrow(
                                            () ->
                                                    new SlotNotFoundException(
                                                            "Slot not found: " + slotId.value()));
                    slot.cancelMeeting(OffsetDateTime.now(clock));
                    slots.update(slot);
                    outbox.append(slot.drainEvents());

                    metrics.meetingCancelled(slotId);
                });
    }
}
