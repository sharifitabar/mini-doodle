package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.support.PostgresIntegrationTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SlotControllerDeleteIT extends PostgresIntegrationTest {

    private static final String HEADER = "X-User-Id";

    @Autowired private MockMvc mockMvc;

    @Test
    void deletesAndReturnsNoContent() throws Exception {
        Caller caller = createCaller();
        String slotId = createSlot(caller);

        mockMvc.perform(
                        delete("/api/v1/slots/{slotId}", slotId)
                                .header(HEADER, caller.userId().value()))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get("/api/v1/slots/{slotId}", slotId)
                                .header(HEADER, caller.userId().value()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnknownCaller() throws Exception {
        Caller caller = createCaller();
        String slotId = createSlot(caller);

        mockMvc.perform(delete("/api/v1/slots/{slotId}", slotId).header(HEADER, UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnknownSlot() throws Exception {
        Caller caller = createCaller();

        mockMvc.perform(
                        delete("/api/v1/slots/{slotId}", UUID.randomUUID())
                                .header(HEADER, caller.userId().value()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDeletingAnotherCallersSlot() throws Exception {
        Caller owner = createCaller();
        Caller other = createCaller();
        String slotId = createSlot(owner);

        mockMvc.perform(
                        delete("/api/v1/slots/{slotId}", slotId)
                                .header(HEADER, other.userId().value()))
                .andExpect(status().isNotFound());
    }

    private String createSlot(Caller caller) throws Exception {
        String body =
                """
                {"startAt":"%s","durationMinutes":30}
                """
                        .formatted(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30).withNano(0));
        String response =
                mockMvc.perform(
                                post("/api/v1/slots")
                                        .header(HEADER, caller.userId().value())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonPath.read(response, "$.id");
    }
}
