package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.domain.exception.SlotOverlapException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives the slot-write retry loop through its injected sleep strategy.
 *
 * <p>The real loop keys off SQLStates that PostgreSQL emits under GiST exclusion-constraint
 * contention. Orchestrating that deterministically would either be flaky or would depend on lock
 * timing, so the transient failures are synthesised here and the sleep is captured instead of
 * performed. Every path of the loop - including the ones that were never executed before this test
 * existed - is covered without a database.
 */
class SlotWriteRetryTest {

    private static final long BACKOFF_MILLIS = 10L;

    private final List<Long> sleeps = new ArrayList<>();

    private SlotWriteRetry retryWith(int maxAttempts) {
        return new SlotWriteRetry(maxAttempts, BACKOFF_MILLIS, sleeps::add);
    }

    /**
     * A transient conflict as it actually arrives: the driver's {@link SQLException} wrapped by a
     * higher layer (jOOQ, or Spring's {@code DataAccessException} translation).
     */
    private static RuntimeException conflictWith(String sqlState) {
        return new RuntimeException("write failed", new SQLException("conflict", sqlState));
    }

    @Test
    void doesNotRetryWhenTheWriteSucceeds() {
        AtomicInteger attempts = new AtomicInteger();

        retryWith(3).run(attempts::incrementAndGet);

        assertThat(attempts).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void retriesAfterADeadlockAndSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeException deadlock = conflictWith(SqlState.DEADLOCK_DETECTED);

        retryWith(3)
                .run(
                        () -> {
                            if (attempts.incrementAndGet() == 1) {
                                throw deadlock;
                            }
                        });

        assertThat(attempts).hasValue(2);
        assertThat(sleeps).containsExactly(BACKOFF_MILLIS);
    }

    @Test
    void retriesAfterASerializationFailureAndSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeException serializationFailure = conflictWith(SqlState.SERIALIZATION_FAILURE);

        retryWith(3)
                .run(
                        () -> {
                            if (attempts.incrementAndGet() == 1) {
                                throw serializationFailure;
                            }
                        });

        assertThat(attempts).hasValue(2);
        assertThat(sleeps).containsExactly(BACKOFF_MILLIS);
    }

    @Test
    void givesUpAndRethrowsOnceTheAttemptsAreExhausted() {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeException deadlock = conflictWith(SqlState.DEADLOCK_DETECTED);

        assertThatThrownBy(
                        () ->
                                retryWith(3)
                                        .run(
                                                () -> {
                                                    attempts.incrementAndGet();
                                                    throw deadlock;
                                                }))
                .isSameAs(deadlock);

        assertThat(attempts).hasValue(3);
        // Backoff grows with each attempt: 10, then 20 - and never at all on the final give-up.
        assertThat(sleeps).containsExactly(BACKOFF_MILLIS, 2 * BACKOFF_MILLIS);
    }

    @Test
    void mapsAnExclusionViolationToADomainOverlapErrorWithoutRetrying() {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeException exclusionViolation = conflictWith(SqlState.EXCLUSION_VIOLATION);

        assertThatThrownBy(
                        () ->
                                retryWith(3)
                                        .run(
                                                () -> {
                                                    attempts.incrementAndGet();
                                                    throw exclusionViolation;
                                                }))
                .isInstanceOf(SlotOverlapException.class)
                .hasMessageContaining("overlaps an existing slot");

        assertThat(attempts).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void rethrowsUnexpectedSqlStatesWithoutRetrying() {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeException foreignKeyViolation = conflictWith(SqlState.FOREIGN_KEY_VIOLATION);

        assertThatThrownBy(
                        () ->
                                retryWith(3)
                                        .run(
                                                () -> {
                                                    attempts.incrementAndGet();
                                                    throw foreignKeyViolation;
                                                }))
                .isSameAs(foreignKeyViolation);

        assertThat(attempts).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void rethrowsFailuresWhoseCauseChainHoldsNoSqlState() {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeException failure = new RuntimeException("no SQLState anywhere");

        assertThatThrownBy(
                        () ->
                                retryWith(3)
                                        .run(
                                                () -> {
                                                    attempts.incrementAndGet();
                                                    throw failure;
                                                }))
                .isSameAs(failure);

        assertThat(attempts).hasValue(1);
    }

    @Test
    void recognisesATransientConflictWrappedDeepInTheCauseChain() {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeException deeplyWrapped =
                new RuntimeException(
                        "outer",
                        new RuntimeException(
                                "inner", new SQLException("x", SqlState.SERIALIZATION_FAILURE)));

        retryWith(2)
                .run(
                        () -> {
                            if (attempts.incrementAndGet() == 1) {
                                throw deeplyWrapped;
                            }
                        });

        assertThat(attempts).hasValue(2);
    }

    @Test
    void reportsAnInterruptedBackoffAndRestoresTheInterruptFlag() {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeException deadlock = conflictWith(SqlState.DEADLOCK_DETECTED);
        SlotWriteRetry retry =
                new SlotWriteRetry(
                        3,
                        BACKOFF_MILLIS,
                        millis -> {
                            throw new InterruptedException("interrupted");
                        });

        assertThatThrownBy(
                        () ->
                                retry.run(
                                        () -> {
                                            attempts.incrementAndGet();
                                            throw deadlock;
                                        }))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(InterruptedException.class);

        assertThat(attempts).hasValue(1);
        // Reads *and clears* the flag, so it cannot leak into later tests on this thread.
        assertThat(Thread.interrupted()).isTrue();
    }
}
