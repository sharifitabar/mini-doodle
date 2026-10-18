package com.minidoodle.calendar.adapter.out.observability;

import com.minidoodle.calendar.application.port.out.AvailabilityMetrics;
import com.minidoodle.calendar.domain.model.Availability;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Availability-query observability: a Micrometer counter/timer plus the structured (key=value) log
 * convention. Kept out of the domain and persistence adapters.
 */
@Component
class MicrometerAvailabilityMetrics implements AvailabilityMetrics {

    private static final Logger log = LoggerFactory.getLogger(MicrometerAvailabilityMetrics.class);

    private final Counter queries;
    private final Timer queryTimer;

    MicrometerAvailabilityMetrics(MeterRegistry registry) {
        this.queries =
                Counter.builder("minidoodle.availability.queries")
                        .description("Number of availability queries")
                        .register(registry);
        this.queryTimer =
                Timer.builder("minidoodle.availability.query")
                        .description("Latency of an availability query")
                        .register(registry);
    }

    @Override
    public <T> T timeQuery(Supplier<T> operation) {
        return queryTimer.record(operation);
    }

    @Override
    public void recordQuery(Availability availability) {
        queries.increment();
        log.info(
                "event=availability.query frameStart={} frameEnd={} intervals={}",
                availability.frame().start(),
                availability.frame().end(),
                availability.intervals().size());
    }
}
