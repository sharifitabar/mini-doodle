package com.minidoodle.calendar.domain.model;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * A user's personal calendar. Modeled as an immutable, behavior-free entity (it has identity but no
 * mutable state in this feature), so a record is used. Time slots are a separate aggregate root
 * that references this calendar by id; the calendar deliberately holds no slot collection, so the
 * cross-slot non-overlap invariant is enforced by the database exclusion constraint rather than by
 * loading the whole collection.
 */
public record Calendar(
        CalendarId id, UserId ownerId, OffsetDateTime createdAt, OffsetDateTime updatedAt) {

    public Calendar {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static Calendar of(
            CalendarId id, UserId ownerId, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new Calendar(id, ownerId, createdAt, updatedAt);
    }
}
