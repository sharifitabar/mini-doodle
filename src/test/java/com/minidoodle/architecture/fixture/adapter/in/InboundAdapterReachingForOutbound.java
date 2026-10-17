package com.minidoodle.architecture.fixture.adapter.in;

import com.minidoodle.architecture.fixture.adapter.out.OutboundAdapterFixture;

/**
 * Deliberately violates "inbound and outbound adapters must not depend on each other" by reaching
 * from an inbound adapter straight into an outbound one.
 */
public class InboundAdapterReachingForOutbound {

    public Class<?> reachOut() {
        return OutboundAdapterFixture.class;
    }
}
