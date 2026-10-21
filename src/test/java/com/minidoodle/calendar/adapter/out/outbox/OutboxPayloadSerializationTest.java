package com.minidoodle.calendar.adapter.out.outbox;

import com.minidoodle.calendar.domain.event.CalendarEvent;
import com.minidoodle.calendar.domain.event.MeetingBooked;
import com.minidoodle.calendar.domain.event.MeetingCancelled;
import com.minidoodle.calendar.domain.event.SlotCreated;
import com.minidoodle.calendar.domain.event.SlotDeleted;
import com.minidoodle.calendar.domain.event.SlotUpdated;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Each event type gives the documented facts, and no participant detail. */
class OutboxPayloadSerializationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-21T09:00:00Z");
    private static final UUID CALENDAR = UUID.randomUUID();
    private static final UUID SLOT = UUID.randomUUID();
    private static final UUID MEETING = UUID.randomUUID();

    private final EventPayload payload = new EventPayload(new ObjectMapper());

    private Map<String, Object> json(CalendarEvent event) throws Exception {
        return new ObjectMapper().readValue(payload.toJson(event), Map.class);
    }

    @Test
    void slotCreatedHasTheTimeTheDurationAndTheStatus() throws Exception {
        SlotCreated event =
                new SlotCreated(UUID.randomUUID(), CALENDAR, SLOT, NOW, 60, "FREE", NOW);

        Map<String, Object> facts = json(event);

        assertThat(facts).containsOnlyKeys("start_at", "duration_minutes", "status");
        assertThat(OffsetDateTime.parse((String) facts.get("start_at"))).isEqualTo(NOW);
        assertThat(facts.get("duration_minutes")).isEqualTo(60);
        assertThat(facts.get("status")).isEqualTo("FREE");
    }

    @Test
    void slotUpdatedHasTheNewValuesAndTheChangedFields() throws Exception {
        SlotUpdated event =
                new SlotUpdated(
                        UUID.randomUUID(),
                        CALENDAR,
                        SLOT,
                        NOW.plusDays(1),
                        45,
                        "BUSY",
                        List.of("start_at", "status"),
                        NOW);

        Map<String, Object> facts = json(event);

        assertThat(facts).containsOnlyKeys("start_at", "duration_minutes", "status", "changed");
        assertThat(facts.get("changed")).isEqualTo(List.of("start_at", "status"));
        assertThat(facts.get("status")).isEqualTo("BUSY");
    }

    @Test
    void slotDeletedHasTheLastValues() throws Exception {
        SlotDeleted event =
                new SlotDeleted(UUID.randomUUID(), CALENDAR, SLOT, NOW, 30, "FREE", NOW);

        assertThat(json(event)).containsOnlyKeys("start_at", "duration_minutes", "status");
    }

    @Test
    void meetingBookedHasTheTitleAndTheParticipantCountOnly() throws Exception {
        MeetingBooked event =
                new MeetingBooked(UUID.randomUUID(), CALENDAR, SLOT, MEETING, "Planning", 2, NOW);

        Map<String, Object> facts = json(event);

        assertThat(facts).containsOnlyKeys("meeting_id", "title", "participant_count");
        assertThat(facts.get("meeting_id")).isEqualTo(MEETING.toString());
        assertThat(facts.get("title")).isEqualTo("Planning");
        assertThat(facts.get("participant_count")).isEqualTo(2);
        assertThat(payload.toJson(event)).doesNotContain("p0@example.com").doesNotContain("name");
    }

    @Test
    void meetingCancelledHasTheMeetingIdentifierOnly() throws Exception {
        MeetingCancelled event =
                new MeetingCancelled(UUID.randomUUID(), CALENDAR, SLOT, MEETING, NOW);

        assertThat(json(event)).containsOnlyKeys("meeting_id");
        assertThat(json(event).get("meeting_id")).isEqualTo(MEETING.toString());
    }
}
