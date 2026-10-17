package com.minidoodle.architecture.fixture.domain;

import org.springframework.stereotype.Component;

/**
 * Deliberately violates the "the domain must not depend on frameworks" rule.
 *
 * <p>Lives in the test sources so the production rule (which excludes tests) never sees it; the
 * rule-effectiveness test imports this package alone and asserts the rule reports it. That is what
 * stops a rule whose package pattern has been misspelled from being vacuously green forever.
 *
 * <p>Nothing here is instantiated, annotated, or component-scanned: the type reference is all the
 * violation that is needed. Deliberately not annotated with a stereotype so that no Spring
 * component scan in the test classpath can pick it up.
 */
public class DomainReachingForSpring {

    public Class<?> reachOut() {
        return Component.class;
    }
}
