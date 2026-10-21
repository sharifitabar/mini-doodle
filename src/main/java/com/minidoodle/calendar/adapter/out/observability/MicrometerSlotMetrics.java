package com.minidoodle.calendar.adapter.out.observability;

import com.minidoodle.calendar.application.port.out.SlotMetrics;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.TimeSlot;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Central place for slot-operation observability: Micrometer counters/timers plus a structured
 * (key=value) log convention. Kept out of the domain and persisted adapters.
 */
@Component
class MicrometerSlotMetrics implements SlotMetrics {

    private final Counter created;
    private final Counter updated;
    private final Counter deleted;
    private final Timer createTimer;
    private final Timer listTimer;

    MicrometerSlotMetrics(MeterRegistry registry) {
        this.created =
                Counter.builder("minidoodle.slots.created")
                        .description("Number of slots created")
                        .register(registry);
        this.updated =
                Counter.builder("minidoodle.slots.updated")
                        .description("Number of slots updated (status or time)")
                        .register(registry);
        this.deleted =
                Counter.builder("minidoodle.slots.deleted")
                        .description("Number of slots deleted")
                        .register(registry);
        this.createTimer =
                Timer.builder("minidoodle.slots.create")
                        .description("Latency of slot creation")
                        .register(registry);
        this.listTimer =
                Timer.builder("minidoodle.slots.list")
                        .description("Latency of slot listing")
                        .register(registry);
    }

    @Override
    public void slotCreated(TimeSlot slot) {
        created.increment();
    }

    @Override
    public void slotUpdated(TimeSlot slot) {
        updated.increment();
    }

    @Override
    public void slotDeleted(SlotId slotId) {
        deleted.increment();
    }

    @Override
    public <T> T timeCreate(Supplier<T> operation) {
        return createTimer.record(operation);
    }

    @Override
    public <T> T timeList(Supplier<T> operation) {
        return listTimer.record(operation);
    }
}
