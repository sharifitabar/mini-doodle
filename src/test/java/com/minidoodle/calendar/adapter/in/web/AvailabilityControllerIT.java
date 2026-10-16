package com.minidoodle.calendar.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AvailabilityControllerIT extends PostgresIntegrationTest {

    private static final String HEADER = "X-User-Id";

    private static final OffsetDateTime BASE =
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withNano(0);

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsOrderedFreeBusyAndUnallocatedIntervalsCoveringTheFrame() throws Exception {
        Caller caller = createCaller();
        insertSlot(caller.calendarId(), BASE, 30, SlotStatus.BUSY);
        UUID meetingSlot = insertSlot(caller.calendarId(), BASE.plusHours(1), 30, SlotStatus.FREE);
        insertMeeting(meetingSlot);

        String json = mockMvc.perform(availability(caller).param("from", BASE.toString())
                        .param("to", BASE.plusHours(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervals.length()").value(4))
                .andExpect(jsonPath("$.intervals[0].kind").value("BUSY"))
                .andExpect(jsonPath("$.intervals[1].kind").value("UNALLOCATED"))
                .andExpect(jsonPath("$.intervals[2].kind").value("BUSY"))
                .andExpect(jsonPath("$.intervals[3].kind").value("UNALLOCATED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(OffsetDateTime.parse(JsonPath.read(json, "$.from")).toInstant())
                .isEqualTo(BASE.toInstant());
        assertThat(OffsetDateTime.parse(JsonPath.read(json, "$.to")).toInstant())
                .isEqualTo(BASE.plusHours(2).toInstant());
        assertContiguousCoverage(json, BASE, BASE.plusHours(2));
    }

    @Test
    void mergesContiguousFreeSlotsIntoOneWindow() throws Exception {
        Caller caller = createCaller();
        insertSlot(caller.calendarId(), BASE, 30, SlotStatus.FREE);
        insertSlot(caller.calendarId(), BASE.plusMinutes(30), 30, SlotStatus.FREE);

        String json = mockMvc.perform(availability(caller).param("from", BASE.toString())
                        .param("to", BASE.plusHours(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervals.length()").value(1))
                .andExpect(jsonPath("$.intervals[0].kind").value("FREE"))
                .andReturn().getResponse().getContentAsString();

        assertThat(OffsetDateTime.parse(JsonPath.read(json, "$.intervals[0].startAt")).toInstant())
                .isEqualTo(BASE.toInstant());
        assertThat(OffsetDateTime.parse(JsonPath.read(json, "$.intervals[0].endAt")).toInstant())
                .isEqualTo(BASE.plusHours(1).toInstant());
    }

    @Test
    void returnsEmptyCalendarAsSingleUnallocatedInterval() throws Exception {
        Caller caller = createCaller();

        String json = mockMvc.perform(availability(caller).param("from", BASE.toString())
                        .param("to", BASE.plusHours(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervals.length()").value(1))
                .andExpect(jsonPath("$.intervals[0].kind").value("UNALLOCATED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(OffsetDateTime.parse(JsonPath.read(json, "$.intervals[0].startAt")).toInstant())
                .isEqualTo(BASE.toInstant());
        assertThat(OffsetDateTime.parse(JsonPath.read(json, "$.intervals[0].endAt")).toInstant())
                .isEqualTo(BASE.plusHours(2).toInstant());
    }

    @Test
    void isolatesAvailabilityPerCaller() throws Exception {
        Caller caller = createCaller();
        Caller other = createCaller();
        insertSlot(other.calendarId(), BASE, 60, SlotStatus.BUSY);

        mockMvc.perform(availability(caller).param("from", BASE.toString())
                        .param("to", BASE.plusHours(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervals.length()").value(1))
                .andExpect(jsonPath("$.intervals[0].kind").value("UNALLOCATED"));
    }

    @Test
    void rejectsUnknownCaller() throws Exception {
        mockMvc.perform(get("/api/v1/availability")
                        .header(HEADER, UUID.randomUUID())
                        .param("from", BASE.toString())
                        .param("to", BASE.plusHours(1).toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsEmptyOrInvertedFrame() throws Exception {
        Caller caller = createCaller();

        mockMvc.perform(availability(caller).param("from", BASE.toString())
                        .param("to", BASE.toString()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(availability(caller).param("from", BASE.toString())
                        .param("to", BASE.minusHours(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsFrameLongerThanMaxSpan() throws Exception {
        Caller caller = createCaller();

        mockMvc.perform(availability(caller).param("from", BASE.toString())
                        .param("to", BASE.plusDays(32).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingFrameParameter() throws Exception {
        Caller caller = createCaller();

        mockMvc.perform(availability(caller).param("from", BASE.toString()))
                .andExpect(status().isBadRequest());
    }

    private MockHttpServletRequestBuilder availability(Caller caller) {
        return get("/api/v1/availability").header(HEADER, caller.userId().value());
    }

    private static void assertContiguousCoverage(String json, OffsetDateTime from, OffsetDateTime to) {
        List<String> starts = JsonPath.read(json, "$.intervals[*].startAt");
        List<String> ends = JsonPath.read(json, "$.intervals[*].endAt");

        OffsetDateTime cursor = from;
        for (int i = 0; i < starts.size(); i++) {
            assertThat(OffsetDateTime.parse(starts.get(i))).isEqualTo(cursor);
            assertThat(OffsetDateTime.parse(ends.get(i))).isAfter(OffsetDateTime.parse(starts.get(i)));
            cursor = OffsetDateTime.parse(ends.get(i));
        }
        assertThat(cursor).isEqualTo(to);
    }
}
