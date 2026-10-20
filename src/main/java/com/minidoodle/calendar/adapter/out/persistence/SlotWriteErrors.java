package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.domain.exception.SlotContentionException;
import com.minidoodle.calendar.domain.exception.SlotOverlapException;

/**
 * Translates the SQLSTATEs PostgreSQL reports during a slot write into domain errors.
 *
 * <p>Two error classes matter. An exclusion-constraint violation ({@value
 * SqlState#EXCLUSION_VIOLATION}) is a business answer: the slot overlaps a committed slot, and a
 * new attempt with the same data fails again. It becomes {@link SlotOverlapException}. A deadlock
 * ({@value SqlState#DEADLOCK_DETECTED}) or a serialization failure ({@value
 * SqlState#SERIALIZATION_FAILURE}) is a timing answer: the write lost a race between concurrent
 * transactions that each waited for an uncommitted range. It becomes {@link
 * SlotContentionException}, and the client decides when to send the request again.
 *
 * <p>This class translates only. It does not run a write again. After a deadlock PostgreSQL stops
 * the whole transaction, so a statement retried inside that transaction would receive {@code 25P02}
 * and could never succeed. Retrying in the request is also a policy the client cannot plan for. See
 * {@code docs/adr/0001-slot-insert-contention.md}.
 *
 * <p>Failures that match neither case are rethrown unchanged: they are defects or definitively
 * unfixable conditions, and a second attempt or a different status code would hide them. The SQL
 * error may arrive as a jOOQ exception or be wrapped as a Spring {@code DataAccessException}, so
 * {@link SqlState#of(Throwable)} inspects the whole cause chain.
 */
final class SlotWriteErrors {

    private SlotWriteErrors() {}

    /** Runs one write and translates the SQLSTATEs of its failure into a domain error. */
    static void translate(Runnable write) {
        try {
            write.run();
        } catch (RuntimeException ex) {
            throw translate(ex);
        }
    }

    private static RuntimeException translate(RuntimeException ex) {
        String sqlState = SqlState.of(ex);
        if (SqlState.EXCLUSION_VIOLATION.equals(sqlState)) {
            return new SlotOverlapException("Slot overlaps an existing slot in the calendar");
        }
        if (SqlState.DEADLOCK_DETECTED.equals(sqlState)
                || SqlState.SERIALIZATION_FAILURE.equals(sqlState)) {
            return new SlotContentionException(
                    "Slot write conflicted with a concurrent write; send the request again");
        }
        return ex;
    }
}
