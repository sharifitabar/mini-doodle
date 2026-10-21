package com.minidoodle.calendar.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One change to the calendar data. The aggregate that makes the change creates the record, and the
 * application service moves it into the outbox in the same transaction.
 *
 * <p>The interface uses framework-free types only, so the domain stays independent of the
 * persistence and web adapters.
 */
public sealed interface CalendarEvent
        permits SlotCreated, SlotUpdated, SlotDeleted, MeetingBooked, MeetingCancelled {

    /** The unique identifier of this event. */
    UUID eventId();

    /** The stable type name, for example {@code slot.created}. */
    String type();

    /** The version of the payload document. It is 1 for this feature. */
    int schemaVersion();

    /** The aggregate family. It is {@code slot} for all 5 types. */
    String aggregateType();

    /** The identifier of the aggregate that changed. */
    UUID aggregateId();

    /** The calendar of the aggregate. */
    UUID calendarId();

    /** The time of the state change, from the caller's clock. */
    OffsetDateTime occurredAt();
}
