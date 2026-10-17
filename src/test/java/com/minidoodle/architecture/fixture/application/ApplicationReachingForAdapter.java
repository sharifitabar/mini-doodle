package com.minidoodle.architecture.fixture.application;

import com.minidoodle.architecture.fixture.adapter.out.OutboundAdapterFixture;

/**
 * Deliberately violates "the application must not depend on adapters" by reaching out to an
 * outbound adapter instead of an output port.
 */
public class ApplicationReachingForAdapter {

    public Class<?> reachOut() {
        return OutboundAdapterFixture.class;
    }
}
