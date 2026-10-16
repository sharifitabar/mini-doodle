package com.minidoodle.calendar.application.service;

import com.minidoodle.calendar.domain.model.Availability;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Availability-query observability: a Micrometer counter/timer plus the structured
 * (key=value) log convention. Kept out of the domain and persistence adapters.
 */
@Component
class AvailabilityMetrics {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityMetrics.class);

    private final Counter queries;
    private final Timer queryTimer;

    AvailabilityMetrics(MeterRegistry registry) {
        this.queries = Counter.builder("minidoodle.availability.queries")
                .description("Number of availability queries").register(registry);
        this.queryTimer = Timer.builder("minidoodle.availability.query")
                .description("Latency of an availability query").register(registry);
    }

    <T> T timeQuery(Supplier<T> operation) {
        return queryTimer.record(operation);
    }

    void recordQuery(Availability availability) {
        queries.increment();
        log.info("event=availability.query frameStart={} frameEnd={} intervals={}",
                availability.frame().start(), availability.frame().end(), availability.intervals().size());
    }
}
