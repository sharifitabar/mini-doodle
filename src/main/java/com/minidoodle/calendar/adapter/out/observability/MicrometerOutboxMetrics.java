package com.minidoodle.calendar.adapter.out.observability;

import com.minidoodle.calendar.application.port.out.OutboxMetrics;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * The outbox meters. The pending count is a cached gauge: the relay writes the value after each
 * poll, so a scrape does not query the table.
 */
@Component
class MicrometerOutboxMetrics implements OutboxMetrics {

    private final Counter published;
    private final Counter failed;
    private final AtomicInteger pending = new AtomicInteger();

    MicrometerOutboxMetrics(MeterRegistry registry) {
        this.published =
                Counter.builder("minidoodle.outbox.published")
                        .description("Number of events delivered to the delivery target")
                        .register(registry);
        this.failed =
                Counter.builder("minidoodle.outbox.failed")
                        .description("Number of failed delivery attempts")
                        .register(registry);
        Gauge.builder("minidoodle.outbox.pending", pending, AtomicInteger::get)
                .description("Number of events that wait for delivery")
                .register(registry);
    }

    @Override
    public void published() {
        published.increment();
    }

    @Override
    public void failed() {
        failed.increment();
    }

    @Override
    public void pending(int count) {
        pending.set(count);
    }
}
