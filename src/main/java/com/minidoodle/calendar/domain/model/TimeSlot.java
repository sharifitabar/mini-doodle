package com.minidoodle.calendar.domain.model;

import com.minidoodle.calendar.domain.exception.InvalidSlotException;
import com.minidoodle.calendar.domain.exception.MeetingConflictException;
import com.minidoodle.calendar.domain.exception.MeetingNotFoundException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * Time slot aggregate root. Owns its optional meeting and all booking transitions, plus a positive,
 * bounded duration, a start instant in the future relative to the caller's clock, and an
 * availability status. Cross-slot non-overlap is enforced by the database exclusion constraint (the
 * only place with a global view).
 */
public final class TimeSlot {

    private final SlotId id;
    private final CalendarId calendarId;
    private final OffsetDateTime createdAt;
    private TimeRange timeRange;
    private SlotStatus status;
    private OffsetDateTime updatedAt;
    private Meeting meeting;

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
        return new TimeSlot(
                SlotId.random(), calendarId, timeRange, SlotStatus.FREE, utcNow, utcNow);
    }

    public static TimeSlot of(
            SlotId id,
            CalendarId calendarId,
            TimeRange timeRange,
            SlotStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        return new TimeSlot(id, calendarId, timeRange, status, createdAt, updatedAt);
    }

    public void changeTime(TimeRange newTimeRange, OffsetDateTime now) {
        ensureEditable();
        Objects.requireNonNull(newTimeRange, "newTimeRange must not be null");
        Objects.requireNonNull(now, "now must not be null");
        requireFutureStart(newTimeRange, now);
        this.timeRange = newTimeRange;
        this.updatedAt = now.withOffsetSameInstant(ZoneOffset.UTC);
    }

    public void changeStatus(SlotStatus newStatus, OffsetDateTime now) {
        ensureEditable();
        Objects.requireNonNull(newStatus, "newStatus must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (this.status == newStatus) {
            return;
        }
        this.status = newStatus;
        this.updatedAt = now.withOffsetSameInstant(ZoneOffset.UTC);
    }

    /** Rehydrates a complete aggregate, including its optional meeting entity. */
    public static TimeSlot restore(TimeSlot slot, Meeting meeting) {
        if (meeting != null && !meeting.slotId().equals(slot.id())) {
            throw new IllegalArgumentException("Meeting belongs to another slot");
        }
        TimeSlot restored =
                of(
                        slot.id(),
                        slot.calendarId(),
                        slot.timeRange(),
                        slot.status(),
                        slot.createdAt(),
                        slot.updatedAt());
        restored.meeting = meeting;
        return restored;
    }

    public Meeting book(
            String title, String description, List<Participant> participants, OffsetDateTime now) {
        if (meeting != null || status != SlotStatus.FREE) {
            throw new MeetingConflictException("Slot is not available for booking");
        }
        Meeting created = Meeting.create(id, title, description, participants, now);
        meeting = created;
        status = SlotStatus.BUSY;
        updatedAt = created.updatedAt();
        return created;
    }

    /** Returns the meeting, or rejects an operation that requires one when none exists. */
    public Meeting requireMeeting() {
        if (meeting == null) {
            throw new MeetingNotFoundException("No meeting on slot " + id.value());
        }
        return meeting;
    }

    /** Returns the optional meeting when absence is an expected result. */
    public java.util.Optional<Meeting> findMeeting() {
        return java.util.Optional.ofNullable(meeting);
    }

    public void cancelMeeting(OffsetDateTime now) {
        Objects.requireNonNull(now, "now must not be null");
        requireMeeting();
        meeting = null;
        status = SlotStatus.FREE;
        updatedAt = now.withOffsetSameInstant(ZoneOffset.UTC);
    }

    public void ensureDeletable() {
        if (meeting != null) {
            throw new MeetingConflictException(
                    "Slot has a meeting; cancel it before deleting the slot");
        }
    }

    public void ensureEditable() {
        if (meeting != null) {
            throw new MeetingConflictException(
                    "Slot has a meeting; cancel it before updating the slot");
        }
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
