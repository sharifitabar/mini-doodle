package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.support.PostgresIntegrationTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SlotControllerReadIT extends PostgresIntegrationTest {

    private static final String HEADER = "X-User-Id";

    @Autowired private MockMvc mockMvc;

    @Test
    void getsCreatedSlotById() throws Exception {
        Caller caller = createCaller();
        String id = createSlot(caller, 30, 30);

        mockMvc.perform(get("/api/v1/slots/{id}", id).header(HEADER, caller.userId().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("FREE"))
                .andExpect(jsonPath("$.durationMinutes").value(30));
    }

    @Test
    void returns404ForUnknownSlot() throws Exception {
        Caller caller = createCaller();

        mockMvc.perform(
                        get("/api/v1/slots/{id}", java.util.UUID.randomUUID())
                                .header(HEADER, caller.userId().value()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns404ForAnotherUsersSlot() throws Exception {
        Caller owner = createCaller();
        Caller other = createCaller();
        String id = createSlot(owner, 30, 30);

        mockMvc.perform(get("/api/v1/slots/{id}", id).header(HEADER, other.userId().value()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns403ForUnknownCaller() throws Exception {
        mockMvc.perform(get("/api/v1/slots").header(HEADER, java.util.UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listsWithFiltersAndPagination() throws Exception {
        Caller caller = createCaller();
        createSlot(caller, 0, 30); // now + 30d
        createSlot(caller, 10 * 24 * 60, 30); // now + 40d
        createSlot(caller, 20 * 24 * 60, 30); // now + 50d

        mockMvc.perform(get("/api/v1/slots").header(HEADER, caller.userId().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.limit").value(50))
                .andExpect(jsonPath("$.offset").value(0));

        mockMvc.perform(
                        get("/api/v1/slots")
                                .header(HEADER, caller.userId().value())
                                .param("limit", "2")
                                .param("offset", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.offset").value(2));

        String from = OffsetDateTime.now(ZoneOffset.UTC).plusDays(35).withNano(0).toString();
        mockMvc.perform(
                        get("/api/v1/slots")
                                .header(HEADER, caller.userId().value())
                                .param("from", from))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));

        mockMvc.perform(
                        get("/api/v1/slots")
                                .header(HEADER, caller.userId().value())
                                .param("status", "FREE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));

        mockMvc.perform(
                        get("/api/v1/slots")
                                .header(HEADER, caller.userId().value())
                                .param("status", "BUSY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void rejectsInvalidLimit() throws Exception {
        Caller caller = createCaller();

        mockMvc.perform(
                        get("/api/v1/slots")
                                .header(HEADER, caller.userId().value())
                                .param("limit", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/api/v1/slots")
                                .header(HEADER, caller.userId().value())
                                .param("limit", "1000"))
                .andExpect(status().isBadRequest());
    }

    private String createSlot(Caller caller, long startOffsetMinutes, int durationMinutes)
            throws Exception {
        OffsetDateTime start =
                OffsetDateTime.now(ZoneOffset.UTC)
                        .plusDays(30)
                        .plusMinutes(startOffsetMinutes)
                        .withNano(0);
        String body =
                """
                {"startAt":"%s","durationMinutes":%d}
                """
                        .formatted(start, durationMinutes);
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
