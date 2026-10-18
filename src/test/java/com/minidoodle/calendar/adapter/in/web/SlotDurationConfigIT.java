package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.support.PostgresIntegrationTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves {@code SLOT_MAX_DURATION_MINUTES} / {@code app.slot.max-duration-minutes} is enforced on
 * the HTTP boundary (not only as dead documentation).
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.slot.max-duration-minutes=60")
class SlotDurationConfigIT extends PostgresIntegrationTest {

    private static final String HEADER = "X-User-Id";

    @Autowired private MockMvc mockMvc;

    @Test
    void rejectsDurationAboveConfiguredMaximum() throws Exception {
        Caller caller = createCaller();

        mockMvc.perform(
                        post("/api/v1/slots")
                                .header(HEADER, caller.userId().value())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(61)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsDurationAtConfiguredMaximum() throws Exception {
        Caller caller = createCaller();

        mockMvc.perform(
                        post("/api/v1/slots")
                                .header(HEADER, caller.userId().value())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(60)))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsPatchDurationAboveConfiguredMaximum() throws Exception {
        Caller caller = createCaller();
        String location =
                mockMvc.perform(
                                post("/api/v1/slots")
                                        .header(HEADER, caller.userId().value())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body(30)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getHeader("Location");

        mockMvc.perform(
                        patch(location)
                                .header(HEADER, caller.userId().value())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"durationMinutes\":61}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        patch(location)
                                .header(HEADER, caller.userId().value())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"durationMinutes\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(60));
    }

    private static String body(int durationMinutes) {
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withNano(0);
        return """
                {"startAt":"%s","durationMinutes":%d}
                """
                .formatted(start, durationMinutes);
    }
}
