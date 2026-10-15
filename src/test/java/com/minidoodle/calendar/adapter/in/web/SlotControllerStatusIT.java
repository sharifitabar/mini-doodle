package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SlotControllerStatusIT extends PostgresIntegrationTest {

    private static final String HEADER = "X-User-Id";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void togglesStatusBetweenBusyAndFree() throws Exception {
        Caller caller = createCaller();
        String slotId = createSlot(caller);

        mockMvc.perform(patch("/api/v1/slots/{slotId}", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BUSY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BUSY"));

        mockMvc.perform(patch("/api/v1/slots/{slotId}", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FREE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FREE"));
    }

    @Test
    void rejectsUnknownCaller() throws Exception {
        Caller caller = createCaller();
        String slotId = createSlot(caller);

        mockMvc.perform(patch("/api/v1/slots/{slotId}", slotId)
                        .header(HEADER, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BUSY\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnknownSlot() throws Exception {
        Caller caller = createCaller();

        mockMvc.perform(patch("/api/v1/slots/{slotId}", UUID.randomUUID())
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BUSY\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsEmptyUpdate() throws Exception {
        Caller caller = createCaller();
        String slotId = createSlot(caller);

        mockMvc.perform(patch("/api/v1/slots/{slotId}", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private String createSlot(Caller caller) throws Exception {
        String body = """
                {"startAt":"%s","durationMinutes":30}
                """.formatted(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30).withNano(0));
        String response = mockMvc.perform(post("/api/v1/slots")
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }
}
