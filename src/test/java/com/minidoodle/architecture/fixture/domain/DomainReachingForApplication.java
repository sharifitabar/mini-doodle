package com.minidoodle.architecture.fixture.domain;

import com.minidoodle.architecture.fixture.application.ApplicationReachingForAdapter;

/**
 * Deliberately violates "the domain depends only on the JDK and itself" by reaching out to the
 * application layer. See {@link DomainReachingForSpring} for why the fixtures live here.
 */
public class DomainReachingForApplication {

    public Class<?> reachOut() {
        return ApplicationReachingForAdapter.class;
    }
}
