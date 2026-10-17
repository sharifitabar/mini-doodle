package com.minidoodle.calendar.fixture.cyclic.b;

import com.minidoodle.calendar.fixture.cyclic.a.CycleA;

/**
 * The other half of the deliberate package cycle that proves the "no cycles" architecture rule is
 * not vacuous. Pairs with {@link CycleA}. Inert otherwise.
 */
public class CycleB {

    public Class<?> reachAround() {
        return CycleA.class;
    }
}
