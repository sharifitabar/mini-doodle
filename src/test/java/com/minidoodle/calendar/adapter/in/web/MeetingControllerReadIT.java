package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.jooq.tables.TimeSlots;
import com.minidoodle.support.PostgresIntegrationTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MeetingControllerReadIT extends PostgresIntegrationTest {

    private static final String HEADER = "X-User-Id";
    private static final String BODY =
            """
            {"title":"Retro","description":"Sprint retro","participants":[
                {"name":"Ada","email":"ada@example.com"},{"email":"guest@example.com"}]}
            """;

    @Autowired private MockMvc mockMvc;

    @Test
    void returnsBookedMeeting() throws Exception {
        Caller caller = createCaller();
        UUID slotId = insertFreeSlot(caller.calendarId());
        book(caller, slotId);

        mockMvc.perform(
                        get("/api/v1/slots/{id}/meeting", slotId)
                                .header(HEADER, caller.userId().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotId").value(slotId.toString()))
                .andExpect(jsonPath("$.title").value("Retro"))
                .andExpect(jsonPath("$.description").value("Sprint retro"))
                .andExpect(jsonPath("$.participants[0].name").value("Ada"))
                .andExpect(jsonPath("$.participants[1].email").value("guest@example.com"))
                .andExpect(jsonPath("$.startAt").isNotEmpty())
                .andExpect(jsonPath("$.endAt").isNotEmpty());
    }

    @Test
    void returnsNotFoundWhenSlotHasNoMeeting() throws Exception {
        Caller caller = createCaller();
        UUID slotId = insertFreeSlot(caller.calendarId());

        mockMvc.perform(
                        get("/api/v1/slots/{id}/meeting", slotId)
                                .header(HEADER, caller.userId().value()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnknownCaller() throws Exception {
        mockMvc.perform(
                        get("/api/v1/slots/{id}/meeting", UUID.randomUUID())
                                .header(HEADER, UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForMissingSlot() throws Exception {
        Caller caller = createCaller();

        mockMvc.perform(
                        get("/api/v1/slots/{id}/meeting", UUID.randomUUID())
                                .header(HEADER, caller.userId().value()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForForeignSlot() throws Exception {
        Caller owner = createCaller();
        Caller intruder = createCaller();
        UUID slotId = insertFreeSlot(owner.calendarId());
        book(owner, slotId);

        mockMvc.perform(
                        get("/api/v1/slots/{id}/meeting", slotId)
                                .header(HEADER, intruder.userId().value()))
                .andExpect(status().isNotFound());
    }

    private void book(Caller caller, UUID slotId) throws Exception {
        mockMvc.perform(
                        post("/api/v1/slots/{id}/meeting", slotId)
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
                .values(
                        id,
                        calendarId.value(),
                        now.plusDays(1),
                        60,
                        SlotStatus.FREE.name(),
                        now,
                        now)
                .execute();
        return id;
    }
}
