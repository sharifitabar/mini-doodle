package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.jooq.tables.TimeSlots;
import com.minidoodle.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MeetingControllerCancelIT extends PostgresIntegrationTest {

    private static final String HEADER = "X-User-Id";
    private static final String BODY = """
            {"title":"Standup","participants":[{"name":"Ada"}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void cancelRemovesMeetingAndFreesSlot() throws Exception {
        Caller caller = createCaller();
        UUID slotId = insertFreeSlot(caller.calendarId());
        book(caller, slotId);

        mockMvc.perform(delete("/api/v1/slots/{id}/meeting", slotId).header(HEADER, caller.userId().value()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/slots/{id}/meeting", slotId).header(HEADER, caller.userId().value()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/slots/{id}", slotId).header(HEADER, caller.userId().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FREE"));
    }

    @Test
    void rejectsUnknownCaller() throws Exception {
        mockMvc.perform(delete("/api/v1/slots/{id}/meeting", UUID.randomUUID())
                        .header(HEADER, UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForMissingSlot() throws Exception {
        Caller caller = createCaller();

        mockMvc.perform(delete("/api/v1/slots/{id}/meeting", UUID.randomUUID())
                        .header(HEADER, caller.userId().value()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenSlotHasNoMeeting() throws Exception {
        Caller caller = createCaller();
        UUID slotId = insertFreeSlot(caller.calendarId());

        mockMvc.perform(delete("/api/v1/slots/{id}/meeting", slotId).header(HEADER, caller.userId().value()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForForeignSlot() throws Exception {
        Caller owner = createCaller();
        Caller intruder = createCaller();
        UUID slotId = insertFreeSlot(owner.calendarId());
        book(owner, slotId);

        mockMvc.perform(delete("/api/v1/slots/{id}/meeting", slotId)
                        .header(HEADER, intruder.userId().value()))
                .andExpect(status().isNotFound());
    }

    private void book(Caller caller, UUID slotId) throws Exception {
        mockMvc.perform(post("/api/v1/slots/{id}/meeting", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated());
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
