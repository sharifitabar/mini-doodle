package com.minidoodle.calendar.adapter.out.observability;

import com.minidoodle.calendar.application.port.out.MeetingMetrics;
import com.minidoodle.calendar.domain.model.Meeting;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.TimeSlot;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Central place for meeting-operation observability: Micrometer counters/timers plus a structured
 * (key=value) log convention. Kept out of the domain and persisted adapters.
 */
@Component
class MicrometerMeetingMetrics implements MeetingMetrics {

    private static final Logger log = LoggerFactory.getLogger(MicrometerMeetingMetrics.class);

    private final Counter booked;
    private final Counter viewed;
    private final Counter cancelled;
    private final Timer bookTimer;
    private final Timer getTimer;
    private final Timer cancelTimer;

    MicrometerMeetingMetrics(MeterRegistry registry) {
        this.booked =
                Counter.builder("minidoodle.meetings.booked")
                        .description("Number of meetings booked")
                        .register(registry);
        this.viewed =
                Counter.builder("minidoodle.meetings.viewed")
                        .description("Number of meeting retrievals")
                        .register(registry);
        this.cancelled =
                Counter.builder("minidoodle.meetings.cancelled")
                        .description("Number of meetings cancelled")
                        .register(registry);
        this.bookTimer =
                Timer.builder("minidoodle.meetings.book")
                        .description("Latency of booking a meeting")
                        .register(registry);
        this.getTimer =
                Timer.builder("minidoodle.meetings.get")
                        .description("Latency of retrieving a meeting")
                        .register(registry);
        this.cancelTimer =
                Timer.builder("minidoodle.meetings.cancel")
                        .description("Latency of cancelling a meeting")
                        .register(registry);
    }

    @Override
    public void meetingBooked(Meeting meeting, TimeSlot slot) {
        booked.increment();
        log.info(
                "event=meeting.booked meetingId={} slotId={} calendarId={} participants={}",
                meeting.id().value(),
                slot.id().value(),
                slot.calendarId().value(),
                meeting.participants().size());
    }

    @Override
    public void meetingViewed(SlotId slotId) {
        viewed.increment();
        log.info("event=meeting.viewed slotId={}", slotId.value());
    }

    @Override
    public void meetingCancelled(SlotId slotId) {
        cancelled.increment();
        log.info("event=meeting.cancelled slotId={}", slotId.value());
    }

    @Override
    public <T> T timeBook(Supplier<T> operation) {
        return bookTimer.record(operation);
    }

    @Override
    public <T> T timeGet(Supplier<T> operation) {
        return getTimer.record(operation);
    }

    @Override
    public void timeCancel(Runnable operation) {
        cancelTimer.record(operation);
    }
}
