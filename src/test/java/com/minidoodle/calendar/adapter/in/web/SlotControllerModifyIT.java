package com.minidoodle.calendar.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import com.minidoodle.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SlotControllerModifyIT extends PostgresIntegrationTest {

    private static final String HEADER = "X-User-Id";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void updatesDurationKeepingStart() throws Exception {
        Caller caller = createCaller();
        OffsetDateTime start = baseStart();
        String slotId = createSlot(caller, start, 30);

        String response = mockMvc.perform(patch("/api/v1/slots/{slotId}", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationMinutes\":45}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(45))
                .andReturn()
                .getResponse()
                .getContentAsString();

        OffsetDateTime returnedStart = OffsetDateTime.parse(JsonPath.read(response, "$.startAt"));
        OffsetDateTime returnedEnd = OffsetDateTime.parse(JsonPath.read(response, "$.endAt"));
        assertThat(returnedStart.toInstant()).isEqualTo(start.toInstant());
        assertThat(returnedEnd.toInstant()).isEqualTo(start.plusMinutes(45).toInstant());
    }

    @Test
    void updatesStartKeepingDuration() throws Exception {
        Caller caller = createCaller();
        OffsetDateTime start = baseStart();
        String slotId = createSlot(caller, start, 30);
        OffsetDateTime newStart = start.plusDays(1);

        mockMvc.perform(patch("/api/v1/slots/{slotId}", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startAt\":\"%s\"}".formatted(newStart)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.startAt").value(newStart.toString()));
    }

    @Test
    void rejectsInvalidDuration() throws Exception {
        Caller caller = createCaller();
        String slotId = createSlot(caller, baseStart(), 30);

        mockMvc.perform(patch("/api/v1/slots/{slotId}", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationMinutes\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsStartInThePast() throws Exception {
        Caller caller = createCaller();
        String slotId = createSlot(caller, baseStart(), 30);
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);

        mockMvc.perform(patch("/api/v1/slots/{slotId}", slotId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startAt\":\"%s\"}".formatted(past)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownSlot() throws Exception {
        Caller caller = createCaller();

        mockMvc.perform(patch("/api/v1/slots/{slotId}", UUID.randomUUID())
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationMinutes\":45}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUpdateIntoOverlappingRange() throws Exception {
        Caller caller = createCaller();
        OffsetDateTime start = baseStart();
        createSlot(caller, start, 60);
        String secondId = createSlot(caller, start.plusMinutes(120), 30);

        mockMvc.perform(patch("/api/v1/slots/{slotId}", secondId)
                        .header(HEADER, caller.userId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startAt\":\"%s\"}".formatted(start.plusMinutes(30))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    private OffsetDateTime baseStart() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(30).withNano(0);
    }

    private String createSlot(Caller caller, OffsetDateTime start, int durationMinutes) throws Exception {
        String body = """
                {"startAt":"%s","durationMinutes":%d}
                """.formatted(start, durationMinutes);
        String response = mockMvc.perform(post("/api/v1/slots")
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
