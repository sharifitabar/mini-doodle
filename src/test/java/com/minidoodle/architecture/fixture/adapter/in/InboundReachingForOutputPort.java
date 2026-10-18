package com.minidoodle.architecture.fixture.adapter.in;

public class InboundReachingForOutputPort {
    public Class<?> dependency() {
        return com.minidoodle.calendar.application.port.out.SlotRepository.class;
    }
}
