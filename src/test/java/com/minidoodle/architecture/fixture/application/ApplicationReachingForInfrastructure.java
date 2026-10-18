package com.minidoodle.architecture.fixture.application;

public class ApplicationReachingForInfrastructure {
    public Class<?> dependency() {
        return io.micrometer.core.instrument.MeterRegistry.class;
    }
}
