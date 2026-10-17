package com.minidoodle.calendar.fixture.cyclic.a;

import com.minidoodle.calendar.fixture.cyclic.b.CycleB;

/**
 * One half of a deliberate package cycle, used to prove the "no cycles" architecture rule is not
 * vacuous. Pairs with {@link CycleB}. Inert otherwise.
 */
public class CycleA {

    public Class<?> reachAround() {
        return CycleB.class;
    }
}
