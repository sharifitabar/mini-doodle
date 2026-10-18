package com.minidoodle.architecture.fixture.adapter.out;

public class OutboundReachingForInbound {
    public Class<?> dependency() {
        return com.minidoodle.architecture.fixture.adapter.in.InboundAdapterReachingForOutbound
                .class;
    }
}
