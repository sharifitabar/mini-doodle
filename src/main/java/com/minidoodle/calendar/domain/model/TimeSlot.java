package com.minidoodle.calendar.domain.model;

import com.minidoodle.calendar.domain.event.CalendarEvent;
import com.minidoodle.calendar.domain.event.MeetingBooked;
import com.minidoodle.calendar.domain.event.MeetingCancelled;
import com.minidoodle.calendar.domain.event.SlotCreated;
import com.minidoodle.calendar.domain.event.SlotDeleted;
import com.minidoodle.calendar.domain.event.SlotUpdated;
import com.minidoodle.calendar.domain.exception.InvalidSlotException;
import com.minidoodle.calendar.domain.exception.MeetingConflictException;
import com.minidoodle.calendar.domain.exception.MeetingNotFoundException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Time slot aggregate root. Owns its optional meeting and all booking transitions, plus a positive,
 * bounded duration, a start instant in the future relative to the caller's clock, and an
 * availability status. Cross-slot non-overlap is enforced by the database exclusion constraint (the
 * only place with a global view).
 *
 * <p>The aggregate records one event for each real transition. {@link #drainEvents()} gives the
 * events to the application service, which writes them to the outbox in the same transaction as the
 * state change.
 */
public final class TimeSlot {

    private final SlotId id;
    private final CalendarId calendarId;
    private final OffsetDateTime createdAt;
    private final List<CalendarEvent> events = new ArrayList<>();
    private final Set<String> pendingChangedFields = new LinkedHashSet<>();
    private TimeRange timeRange;
    private SlotStatus status;
    private OffsetDateTime updatedAt;
    private Meeting meeting;
    private OffsetDateTime pendingChangedAt;

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
        TimeSlot created =
                new TimeSlot(
                        SlotId.random(), calendarId, timeRange, SlotStatus.FREE, utcNow, utcNow);
        created.events.add(
                new SlotCreated(
                        UUID.randomUUID(),
                        calendarId.value(),
                        created.id.value(),
                        timeRange.start(),
                        timeRange.durationMinutes(),
                        SlotStatus.FREE.name(),
                        utcNow));
        return created;
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
        if (this.timeRange.equals(newTimeRange)) {
            return;
        }
        boolean startChanged = !this.timeRange.start().equals(newTimeRange.start());
        boolean durationChanged =
                this.timeRange.durationMinutes() != newTimeRange.durationMinutes();
        this.timeRange = newTimeRange;
        this.updatedAt = now.withOffsetSameInstant(ZoneOffset.UTC);
        if (startChanged) {
            markChanged("start_at", updatedAt);
        }
        if (durationChanged) {
            markChanged("duration_minutes", updatedAt);
        }
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
        markChanged("status", updatedAt);
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
        events.add(
                new MeetingBooked(
                        UUID.randomUUID(),
                        calendarId.value(),
                        id.value(),
                        created.id().value(),
                        created.title(),
                        created.participants().size(),
                        created.updatedAt()));
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
        Meeting cancelled = requireMeeting();
        meeting = null;
        status = SlotStatus.FREE;
        updatedAt = now.withOffsetSameInstant(ZoneOffset.UTC);
        events.add(
                new MeetingCancelled(
                        UUID.randomUUID(),
                        calendarId.value(),
                        id.value(),
                        cancelled.id().value(),
                        updatedAt));
    }

    /**
     * Records the deletion event. The caller deletes the row in the same transaction, so a failed
     * delete leaves no event behind.
     */
    public void recordDeletion(OffsetDateTime now) {
        Objects.requireNonNull(now, "now must not be null");
        OffsetDateTime occurredAt = now.withOffsetSameInstant(ZoneOffset.UTC);
        events.add(
                new SlotDeleted(
                        UUID.randomUUID(),
                        calendarId.value(),
                        id.value(),
                        timeRange.start(),
                        timeRange.durationMinutes(),
                        status.name(),
                        occurredAt));
    }

    /**
     * Returns the events that this aggregate recorded, then clears them. The pending field set
     * merges the changes of one command, so a patch that changes time and status gives one {@code
     * slot.updated} event.
     */
    public List<CalendarEvent> drainEvents() {
        List<CalendarEvent> drained = new ArrayList<>(events);
        if (!pendingChangedFields.isEmpty()) {
            drained.add(
                    new SlotUpdated(
                            UUID.randomUUID(),
                            calendarId.value(),
                            id.value(),
                            timeRange.start(),
                            timeRange.durationMinutes(),
                            status.name(),
                            List.copyOf(pendingChangedFields),
                            pendingChangedAt));
        }
        events.clear();
        pendingChangedFields.clear();
        pendingChangedAt = null;
        return List.copyOf(drained);
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

    private void markChanged(String field, OffsetDateTime occurredAt) {
        pendingChangedFields.add(field);
        pendingChangedAt = occurredAt;
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
