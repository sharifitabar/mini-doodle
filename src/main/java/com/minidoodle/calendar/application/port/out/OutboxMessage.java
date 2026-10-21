package com.minidoodle.calendar.application.port.out;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One stored event, as a delivery adapter receives it. The envelope fields come from the table
 * columns and the payload document comes from the {@code payload} column. {@code attempts} is the
 * count of failed deliveries before this one.
 */
public record OutboxMessage(
        long sequence,
        UUID eventId,
        String eventType,
        int schemaVersion,
        String aggregateType,
        UUID aggregateId,
        UUID calendarId,
        OffsetDateTime occurredAt,
        String payloadJson,
        int attempts) {}
