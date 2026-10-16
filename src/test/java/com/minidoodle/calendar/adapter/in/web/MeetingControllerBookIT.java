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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MeetingControllerBookIT extends PostgresIntegrationTest {

    private static final String HEADER = "X-User-Id";
    private static final String VALID_BODY = """
            {"title":"Sprint planning","description":"Kick off","participants":[
                {"name":"Ada","email":"ada@example.com"},{"email":"guest@example.com"}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void booksFreeSlotAndMarksItBusy() throws Exception {
        Caller caller = createCaller();
        UUID slotId = insertSlot(caller.calendarId(), SlotStatus.FREE);

        mockMvc.perform(post("/api/v1/slots/{id}/meeting", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slotId").value(slotId.toString()))
                .andExpect(jsonPath("$.title").value("Sprint planning"))
                .andExpect(jsonPath("$.description").value("Kick off"))
                .andExpect(jsonPath("$.participants[0].name").value("Ada"))
                .andExpect(jsonPath("$.participants[1].email").value("guest@example.com"))
                .andExpect(jsonPath("$.startAt").isNotEmpty())
                .andExpect(jsonPath("$.endAt").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/slots/{id}", slotId).header(HEADER, caller.userId().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BUSY"));
    }

    @Test
    void rejectsBlankTitle() throws Exception {
        Caller caller = createCaller();
        UUID slotId = insertSlot(caller.calendarId(), SlotStatus.FREE);

        mockMvc.perform(post("/api/v1/slots/{id}/meeting", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   ","participants":[{"name":"Ada"}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsEmptyParticipants() throws Exception {
        Caller caller = createCaller();
        UUID slotId = insertSlot(caller.calendarId(), SlotStatus.FREE);

        mockMvc.perform(post("/api/v1/slots/{id}/meeting", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Title","participants":[]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsParticipantWithoutNameOrEmail() throws Exception {
        Caller caller = createCaller();
        UUID slotId = insertSlot(caller.calendarId(), SlotStatus.FREE);

        mockMvc.perform(post("/api/v1/slots/{id}/meeting", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Title","participants":[{"name":"   ","email":""}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownCaller() throws Exception {
        mockMvc.perform(post("/api/v1/slots/{id}/meeting", UUID.randomUUID())
                        .header(HEADER, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForMissingSlot() throws Exception {
        Caller caller = createCaller();

        mockMvc.perform(post("/api/v1/slots/{id}/meeting", UUID.randomUUID())
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForForeignSlot() throws Exception {
        Caller owner = createCaller();
        Caller intruder = createCaller();
        UUID slotId = insertSlot(owner.calendarId(), SlotStatus.FREE);

        mockMvc.perform(post("/api/v1/slots/{id}/meeting", slotId)
                        .header(HEADER, intruder.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsConflictForBusySlot() throws Exception {
        Caller caller = createCaller();
        UUID slotId = insertSlot(caller.calendarId(), SlotStatus.BUSY);

        mockMvc.perform(post("/api/v1/slots/{id}/meeting", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsSecondBookingOfSameSlot() throws Exception {
        Caller caller = createCaller();
        UUID slotId = insertSlot(caller.calendarId(), SlotStatus.FREE);

        mockMvc.perform(post("/api/v1/slots/{id}/meeting", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/slots/{id}/meeting", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    private UUID insertSlot(CalendarId calendarId, SlotStatus status) {
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
                .values(id, calendarId.value(), now.plusDays(1), 60, status.name(), now, now)
                .execute();
        return id;
    }
}
