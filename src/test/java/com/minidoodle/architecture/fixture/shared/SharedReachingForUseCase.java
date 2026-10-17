package com.minidoodle.architecture.fixture.shared;

import com.minidoodle.architecture.fixture.application.ApplicationReachingForAdapter;

/**
 * Deliberately violates "shared infrastructure must not depend on the application or the adapters"
 * by reaching from a shared component into an application type.
 */
public class SharedReachingForUseCase {

    public Class<?> reachOut() {
        return ApplicationReachingForAdapter.class;
    }
}
