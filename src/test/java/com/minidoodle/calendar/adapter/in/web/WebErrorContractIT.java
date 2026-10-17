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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The request-binding and caller-identity failures that are reachable through the public API.
 *
 * <p>Each case asserts the status <em>and</em> the problem {@code type} and {@code detail}, so a
 * mapping that silently changed shape (or fell through to Spring's default error body) fails here
 * rather than passing as "some 400".
 *
 * <p>The mappings that the public API cannot provoke are covered next door, in {@code
 * com.minidoodle.shared.web.ExceptionHandlerAdviceTest}.
 */
@AutoConfigureMockMvc
class WebErrorContractIT extends PostgresIntegrationTest {

    private static final String HEADER = "X-User-Id";
    private static final String PROBLEMS = "https://minidoodle.example/problems/";

    @Autowired private MockMvc mockMvc;

    @Test
    void reportsAnUnparseableSlotIdAsAValidationProblem() throws Exception {
        mockMvc.perform(get("/api/v1/slots/not-a-uuid").header(HEADER, UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value("Invalid value for 'slotId'"));
    }

    @Test
    void reportsAnUnreadableBodyAsAValidationProblem() throws Exception {
        Caller caller = createCaller();

        mockMvc.perform(
                        post("/api/v1/slots")
                                .header(HEADER, caller.userId().value())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"startAt\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation"))
                .andExpect(jsonPath("$.detail").value("Malformed or unreadable request body"));
    }

    @Test
    void reportsABlankCallerHeaderAsAnInvalidCallerProblem() throws Exception {
        mockMvc.perform(
                        post("/api/v1/slots")
                                .header(HEADER, "   ")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validSlotBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-caller"))
                .andExpect(jsonPath("$.title").value("Invalid caller identity"))
                .andExpect(jsonPath("$.detail").value("The X-User-Id header is required"));
    }

    @Test
    void reportsANonUuidCallerHeaderAsAnInvalidCallerProblem() throws Exception {
        mockMvc.perform(
                        post("/api/v1/slots")
                                .header(HEADER, "not-a-uuid")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validSlotBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-caller"))
                .andExpect(
                        jsonPath("$.detail")
                                .value("The X-User-Id header is not a valid UUID: not-a-uuid"));
    }

    private String validSlotBody() {
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30).withNano(0);
        return """
                {"startAt":"%s","durationMinutes":30}
                """
                .formatted(start);
    }
}
