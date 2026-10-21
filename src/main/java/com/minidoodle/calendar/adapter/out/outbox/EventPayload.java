package com.minidoodle.calendar.adapter.out.outbox;

import com.minidoodle.calendar.domain.event.CalendarEvent;
import com.minidoodle.calendar.domain.event.MeetingBooked;
import com.minidoodle.calendar.domain.event.MeetingCancelled;
import com.minidoodle.calendar.domain.event.SlotCreated;
import com.minidoodle.calendar.domain.event.SlotDeleted;
import com.minidoodle.calendar.domain.event.SlotUpdated;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds the changed-facts document of one event. The document holds no participant name and no
 * participant email. Dates are ISO-8601 instants, so the document does not depend on the Jackson
 * date configuration.
 */
final class EventPayload {

    private final ObjectMapper json;

    EventPayload(ObjectMapper json) {
        this.json = json;
    }

    String toJson(CalendarEvent event) {
        try {
            return json.writeValueAsString(facts(event));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize the event payload", ex);
        }
    }

    Map<String, Object> facts(CalendarEvent event) {
        Map<String, Object> facts = new LinkedHashMap<>();
        if (event instanceof SlotCreated created) {
            facts.put("start_at", created.startAt().toString());
            facts.put("duration_minutes", created.durationMinutes());
            facts.put("status", created.status());
        } else if (event instanceof SlotUpdated updated) {
            facts.put("start_at", updated.startAt().toString());
            facts.put("duration_minutes", updated.durationMinutes());
            facts.put("status", updated.status());
            facts.put("changed", updated.changed());
        } else if (event instanceof SlotDeleted deleted) {
            facts.put("start_at", deleted.startAt().toString());
            facts.put("duration_minutes", deleted.durationMinutes());
            facts.put("status", deleted.status());
        } else if (event instanceof MeetingBooked booked) {
            facts.put("meeting_id", booked.meetingId().toString());
            facts.put("title", booked.title());
            facts.put("participant_count", booked.participantCount());
        } else if (event instanceof MeetingCancelled cancelled) {
            facts.put("meeting_id", cancelled.meetingId().toString());
        }
        return facts;
    }
}
