package com.minidoodle.calendar.adapter.out.persistence;

import java.sql.SQLException;

/**
 * The SQLState values this persistence adapter distinguishes, plus the cause-chain walk that finds
 * one.
 *
 * <p>Driver exceptions arrive wrapped by jOOQ or by Spring's {@code DataAccessException}
 * translation, so the state is read from the first {@link SQLException} anywhere in the cause chain
 * rather than from the top-level throwable.
 */
final class SqlState {

    static final String EXCLUSION_VIOLATION = "23P01";
    static final String DEADLOCK_DETECTED = "40P01";
    static final String SERIALIZATION_FAILURE = "40001";
    static final String UNIQUE_VIOLATION = "23505";
    static final String FOREIGN_KEY_VIOLATION = "23503";

    private SqlState() {}

    /** Returns the SQLState of the first {@link SQLException} in the cause chain, if any. */
    static String of(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
        }
        return null;
    }
}
