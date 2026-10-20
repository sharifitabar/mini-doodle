package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.domain.exception.SlotContentionException;
import com.minidoodle.calendar.domain.exception.SlotOverlapException;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the SQLSTATE translation of slot writes.
 *
 * <p>The states are always reported by PostgreSQL during a write; the failure is never provoked by
 * a database here, because a deadlock cannot be requested on demand. The synthetic failures keep
 * the translation deterministic and prove every branch of it, including the rethrow paths that no
 * integration test reaches.
 */
class SlotWriteErrorsTest {

    /**
     * A driver failure as it actually arrives: the {@link SQLException} is wrapped by a higher
     * layer.
     */
    private static RuntimeException failureWith(String sqlState) {
        return new RuntimeException("write failed", new SQLException("conflict", sqlState));
    }

    @Test
    void runsTheWriteAndReturnsWhenNothingFails() {
        AtomicInteger calls = new AtomicInteger();

        SlotWriteErrors.translate(calls::incrementAndGet);

        assertThat(calls).hasValue(1);
    }

    @Test
    void mapsAnExclusionViolationToAnOverlapError() {
        RuntimeException exclusionViolation = failureWith(SqlState.EXCLUSION_VIOLATION);

        assertThatThrownBy(
                        () ->
                                SlotWriteErrors.translate(
                                        () -> {
                                            throw exclusionViolation;
                                        }))
                .isInstanceOf(SlotOverlapException.class)
                .hasMessageContaining("overlaps an existing slot");
    }

    @Test
    void mapsADeadlockToAContentionError() {
        RuntimeException deadlock = failureWith(SqlState.DEADLOCK_DETECTED);

        assertThatThrownBy(
                        () ->
                                SlotWriteErrors.translate(
                                        () -> {
                                            throw deadlock;
                                        }))
                .isInstanceOf(SlotContentionException.class)
                .hasMessageContaining("send the request again");
    }

    @Test
    void mapsASerializationFailureToAContentionError() {
        RuntimeException serializationFailure = failureWith(SqlState.SERIALIZATION_FAILURE);

        assertThatThrownBy(
                        () ->
                                SlotWriteErrors.translate(
                                        () -> {
                                            throw serializationFailure;
                                        }))
                .isInstanceOf(SlotContentionException.class);
    }

    @Test
    void findsTheSqlStateDeepInTheCauseChain() {
        RuntimeException deeplyWrapped =
                new RuntimeException(
                        "outer",
                        new RuntimeException(
                                "inner", new SQLException("x", SqlState.DEADLOCK_DETECTED)));

        assertThatThrownBy(
                        () ->
                                SlotWriteErrors.translate(
                                        () -> {
                                            throw deeplyWrapped;
                                        }))
                .isInstanceOf(SlotContentionException.class);
    }

    @Test
    void rethrowsAnUnexpectedSqlStateUnchanged() {
        RuntimeException foreignKeyViolation = failureWith(SqlState.FOREIGN_KEY_VIOLATION);

        assertThatThrownBy(
                        () ->
                                SlotWriteErrors.translate(
                                        () -> {
                                            throw foreignKeyViolation;
                                        }))
                .isSameAs(foreignKeyViolation);
    }

    @Test
    void rethrowsAFailureWithoutASqlStateUnchanged() {
        RuntimeException failure = new RuntimeException("no SQLState anywhere");

        assertThatThrownBy(
                        () ->
                                SlotWriteErrors.translate(
                                        () -> {
                                            throw failure;
                                        }))
                .isSameAs(failure);
    }
}
