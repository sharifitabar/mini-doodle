package com.minidoodle.calendar.domain.model;

import com.minidoodle.calendar.domain.exception.InvalidSlotException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Time slot aggregate. Owns the invariants of a single bookable slot:
 * a positive, bounded duration, a start instant in the future relative to the
 * caller's clock, and an availability status. Cross-slot non-overlap is enforced
 * by the database exclusion constraint (the only place with a global view).
 */
public final class TimeSlot {

    private final SlotId id;
    private final CalendarId calendarId;
    private final OffsetDateTime createdAt;
    private TimeRange timeRange;
    private SlotStatus status;
    private OffsetDateTime updatedAt;

    private TimeSlot(
            SlotId id,
            CalendarId calendarId,
            TimeRange timeRange,
            SlotStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.calendarId = Objects.requireNonNull(calendarId, "calendarId must not be null");
        this.timeRange = Objects.requireNonNull(timeRange, "timeRange must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static TimeSlot create(CalendarId calendarId, TimeRange timeRange, OffsetDateTime now) {
        Objects.requireNonNull(now, "now must not be null");
        requireFutureStart(timeRange, now);
        OffsetDateTime utcNow = now.withOffsetSameInstant(ZoneOffset.UTC);
        return new TimeSlot(SlotId.random(), calendarId, timeRange, SlotStatus.FREE, utcNow, utcNow);
    }

    public static TimeSlot rehydrate(
            SlotId id,
            CalendarId calendarId,
            TimeRange timeRange,
            SlotStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        return new TimeSlot(id, calendarId, timeRange, status, createdAt, updatedAt);
    }

    public void changeTime(TimeRange newTimeRange, OffsetDateTime now) {
        Objects.requireNonNull(newTimeRange, "newTimeRange must not be null");
        Objects.requireNonNull(now, "now must not be null");
        requireFutureStart(newTimeRange, now);
        this.timeRange = newTimeRange;
        this.updatedAt = now.withOffsetSameInstant(ZoneOffset.UTC);
    }

    public void changeStatus(SlotStatus newStatus, OffsetDateTime now) {
        Objects.requireNonNull(newStatus, "newStatus must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (this.status == newStatus) {
            return;
        }
        this.status = newStatus;
        this.updatedAt = now.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static void requireFutureStart(TimeRange timeRange, OffsetDateTime now) {
        Objects.requireNonNull(timeRange, "timeRange must not be null");
        if (!timeRange.start().isAfter(now)) {
            throw new InvalidSlotException("start must be in the future");
        }
    }

    public SlotId id() {
        return id;
    }

    public CalendarId calendarId() {
        return calendarId;
    }

    public TimeRange timeRange() {
        return timeRange;
    }

    public SlotStatus status() {
        return status;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}
