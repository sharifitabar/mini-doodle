package com.minidoodle.calendar.application.service;

import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.TimeSlot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Central place for slot-operation observability: Micrometer counters/timers plus a
 * structured (key=value) log convention. Kept out of the domain and persisted adapters.
 */
@Component
class SlotMetrics {

    private static final Logger log = LoggerFactory.getLogger(SlotMetrics.class);

    private final Counter created;
    private final Counter updated;
    private final Counter deleted;
    private final Timer createTimer;
    private final Timer listTimer;

    SlotMetrics(MeterRegistry registry) {
        this.created = Counter.builder("minidoodle.slots.created")
                .description("Number of slots created").register(registry);
        this.updated = Counter.builder("minidoodle.slots.updated")
                .description("Number of slots updated (status or time)").register(registry);
        this.deleted = Counter.builder("minidoodle.slots.deleted")
                .description("Number of slots deleted").register(registry);
        this.createTimer = Timer.builder("minidoodle.slots.create")
                .description("Latency of slot creation").register(registry);
        this.listTimer = Timer.builder("minidoodle.slots.list")
                .description("Latency of slot listing").register(registry);
    }

    void slotCreated(TimeSlot slot) {
        created.increment();
        log.info("event=slot.created slotId={} calendarId={}",
                slot.id().value(), slot.calendarId().value());
    }

    void slotUpdated(TimeSlot slot) {
        updated.increment();
        log.info("event=slot.updated slotId={} calendarId={} status={} startAt={} durationMinutes={}",
                slot.id().value(), slot.calendarId().value(), slot.status(),
                slot.timeRange().start(), slot.timeRange().durationMinutes());
    }

    void slotDeleted(SlotId slotId) {
        deleted.increment();
        log.info("event=slot.deleted slotId={}", slotId.value());
    }

    <T> T timeCreate(Supplier<T> operation) {
        return createTimer.record(operation);
    }

    <T> T timeList(Supplier<T> operation) {
        return listTimer.record(operation);
    }
}
