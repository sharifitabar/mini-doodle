package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.domain.exception.SlotOverlapException;

/**
 * Runs a slot write with bounded retries for the transient concurrency errors PostgreSQL reports
 * when a GiST exclusion constraint is contended.
 *
 * <p>Concurrent inserts into the {@code ex_slot_no_overlap} exclusion constraint can fail with a
 * deadlock ({@value SqlState#DEADLOCK_DETECTED}) or a serialization failure ({@value
 * SqlState#SERIALIZATION_FAILURE}). Those are transient: retrying lets the write converge on the
 * definitive exclusion violation ({@value SqlState#EXCLUSION_VIOLATION}) once the winning
 * transaction commits, which is surfaced as a domain-level overlap error rather than a raw
 * persistence exception. The SQL error may arrive as a jOOQ exception or wrapped as a Spring {@code
 * DataAccessException}, so {@link SqlState#of(Throwable)} inspects the whole cause chain.
 *
 * <p>The sleep strategy is injectable so the retry behaviour can be tested deterministically: real
 * deadlocks cannot be provoked on demand, and a test that only sometimes provokes one would be
 * flaky.
 */
final class SlotWriteRetry {

    static final int DEFAULT_MAX_ATTEMPTS = 6;
    static final long DEFAULT_BACKOFF_MILLIS = 10L;

    private final int maxAttempts;
    private final long backoffMillis;
    private final Sleeper sleeper;

    SlotWriteRetry() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_BACKOFF_MILLIS, SlotWriteRetry::sleep);
    }

    SlotWriteRetry(int maxAttempts, long backoffMillis, Sleeper sleeper) {
        this.maxAttempts = maxAttempts;
        this.backoffMillis = backoffMillis;
        this.sleeper = sleeper;
    }

    /** How the retry loop waits between attempts; injectable so tests never really sleep. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    void run(Runnable action) {
        for (int attempt = 1; ; attempt++) {
            try {
                action.run();
                return;
            } catch (RuntimeException ex) {
                String sqlState = SqlState.of(ex);
                if (SqlState.EXCLUSION_VIOLATION.equals(sqlState)) {
                    throw new SlotOverlapException(
                            "Slot overlaps an existing slot in the calendar");
                }
                boolean transientConflict =
                        SqlState.DEADLOCK_DETECTED.equals(sqlState)
                                || SqlState.SERIALIZATION_FAILURE.equals(sqlState);
                if (transientConflict && attempt < maxAttempts) {
                    backoff(attempt);
                    continue;
                }
                throw ex;
            }
        }
    }

    private void backoff(int attempt) {
        try {
            sleeper.sleep(backoffMillis * attempt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying slot write", interrupted);
        }
    }

    private static void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
