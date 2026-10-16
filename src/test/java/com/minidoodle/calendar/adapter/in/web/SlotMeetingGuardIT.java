package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.jooq.tables.TimeSlots;
import com.minidoodle.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-010: a slot holding a meeting cannot be modified or deleted until the meeting is cancelled.
 */
@AutoConfigureMockMvc
class SlotMeetingGuardIT extends PostgresIntegrationTest {

    private static final String HEADER = "X-User-Id";
    private static final String BOOK_BODY = """
            {"title":"Standup","participants":[{"name":"Ada"}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    private Caller caller;
    private UUID slotId;

    @BeforeEach
    void bookMeeting() throws Exception {
        caller = createCaller();
        slotId = insertFreeSlot(caller.calendarId());
        mockMvc.perform(post("/api/v1/slots/{id}/meeting", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOK_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsStatusChangeOnBookedSlot() throws Exception {
        mockMvc.perform(patch("/api/v1/slots/{id}", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"FREE"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsDurationChangeOnBookedSlot() throws Exception {
        mockMvc.perform(patch("/api/v1/slots/{id}", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"durationMinutes":30}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsTimeChangeOnBookedSlot() throws Exception {
        String startAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).toString();
        mockMvc.perform(patch("/api/v1/slots/{id}", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startAt":"%s"}
                                """.formatted(startAt)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsDeleteOnBookedSlot() throws Exception {
        mockMvc.perform(delete("/api/v1/slots/{id}", slotId).header(HEADER, caller.userId().value()))
                .andExpect(status().isConflict());
    }

    private UUID insertFreeSlot(CalendarId calendarId) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(TimeSlots.TIME_SLOTS)
                .columns(
                        TimeSlots.TIME_SLOTS.ID,
                        TimeSlots.TIME_SLOTS.CALENDAR_ID,
                        TimeSlots.TIME_SLOTS.START_AT,
                        TimeSlots.TIME_SLOTS.DURATION_MINUTES,
                        TimeSlots.TIME_SLOTS.STATUS,
                        TimeSlots.TIME_SLOTS.CREATED_AT,
                        TimeSlots.TIME_SLOTS.UPDATED_AT)
                .values(id, calendarId.value(), now.plusDays(1), 60, SlotStatus.FREE.name(), now, now)
                .execute();
        return id;
    }
}
