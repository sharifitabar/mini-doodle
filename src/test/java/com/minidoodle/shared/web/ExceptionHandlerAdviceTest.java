package com.minidoodle.shared.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import qualitygate.advice.AdviceProbeController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The {@link GlobalExceptionHandler} mappings that the public API cannot provoke, plus the
 * framework-level binding failure it maps.
 *
 * <p>Several mappings here are unreachable over HTTP, and not by accident:
 *
 * <ul>
 *   <li>{@code InvalidSlotException} / {@code InvalidMeetingException} - request bean validation
 *       rejects the offending inputs first ({@code @Future}, {@code @Min}/{@code @Max}, and
 *       {@code @AssertTrue} on {@code UpdateSlotRequest} and {@code ParticipantRequest}), so the
 *       domain constructors that throw these can no longer be reached from a real request;
 *   <li>{@code MissingRequestHeaderException} - the API takes its caller identity through a custom
 *       {@code @CurrentUser} argument resolver instead of a declared {@code @RequestHeader}.
 *   <li>{@code SlotContentionException} - only a database deadlock produces it, and a deadlock
 *       cannot be requested on demand.
 * </ul>
 *
 * <p>Rather than delete working defensive mappings or pretend the paths are live, they are
 * exercised here through the real advice and the real dispatcher over a throwaway controller. That
 * is what makes the class-scoped coverage floor on the advice meaningful: every mapping is asserted
 * to produce the documented status, problem type, title, and detail - not merely to have executed.
 *
 * <p>The throwaway controller lives outside {@code com.minidoodle}, off the component-scan path;
 * see {@link AdviceProbeController} for why.
 */
class ExceptionHandlerAdviceTest {

    private static final String PROBLEMS = "https://minidoodle.example/problems/";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new AdviceProbeController())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void mapsAMissingRequiredHeaderToAValidationProblem() throws Exception {
        mockMvc.perform(get("/unreachable/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value("Missing required header: X-Required"));
    }

    @Test
    void mapsAnInvalidSlotFailureToAnInvalidSlotProblem() throws Exception {
        mockMvc.perform(get("/unreachable/invalid-slot"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-slot"))
                .andExpect(jsonPath("$.title").value("Invalid slot"))
                .andExpect(jsonPath("$.detail").value("start must be in the future"));
    }

    @Test
    void mapsAnInvalidMeetingFailureToAnInvalidMeetingProblem() throws Exception {
        mockMvc.perform(get("/unreachable/invalid-meeting"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-meeting"))
                .andExpect(jsonPath("$.title").value("Invalid meeting"))
                .andExpect(
                        jsonPath("$.detail").value("A participant must have a name or an email"));
    }

    @Test
    void mapsSlotContentionToAServiceUnavailableProblemWithRetryAfter() throws Exception {
        mockMvc.perform(get("/unreachable/contention"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "slot-contention"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(
                        jsonPath("$.title").value("Slot write conflicts with a concurrent write"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Slot write conflicted with a concurrent write; "
                                                + "send the request again"));
    }
}
