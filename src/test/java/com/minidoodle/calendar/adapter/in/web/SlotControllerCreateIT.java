package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SlotControllerCreateIT extends PostgresIntegrationTest {

    private static final String HEADER = "X-User-Id";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsSlotWithDefaultFreeStatus() throws Exception {
        Caller caller = createCaller();

        mockMvc.perform(post("/api/v1/slots")
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(60, 30)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("FREE"))
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.startAt").isNotEmpty())
                .andExpect(jsonPath("$.endAt").isNotEmpty());
    }

    @Test
    void rejectsMissingCallerHeader() throws Exception {
        mockMvc.perform(post("/api/v1/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(60, 30)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedCallerHeader() throws Exception {
        mockMvc.perform(post("/api/v1/slots")
                        .header(HEADER, "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(60, 30)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownCaller() throws Exception {
        mockMvc.perform(post("/api/v1/slots")
                        .header(HEADER, java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(60, 30)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidDuration() throws Exception {
        Caller caller = createCaller();

        mockMvc.perform(post("/api/v1/slots")
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(60, 0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsStartInThePast() throws Exception {
        Caller caller = createCaller();
        String pastBody = """
                {"startAt":"%s","durationMinutes":30}
                """.formatted(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).withNano(0));

        mockMvc.perform(post("/api/v1/slots")
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pastBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsOverlappingSlot() throws Exception {
        Caller caller = createCaller();

        mockMvc.perform(post("/api/v1/slots")
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(60, 60)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/slots")
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(90, 30)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    private String body(long startOffsetMinutes, int durationMinutes) {
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(30)
                .plusMinutes(startOffsetMinutes)
                .withNano(0);
        return """
                {"startAt":"%s","durationMinutes":%d}
                """.formatted(start, durationMinutes);
    }
}
