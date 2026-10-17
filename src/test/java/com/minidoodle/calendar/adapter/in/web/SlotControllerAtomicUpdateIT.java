package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.jooq.tables.Calendars;
import com.minidoodle.jooq.tables.TimeSlots;
import com.minidoodle.jooq.tables.Users;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A single PATCH that changes both status and time must be atomic: when the time change is rejected
 * (it overlaps another slot), the status change must not persist.
 *
 * <p>Not transactional on purpose — the failed request and the read-back must run on separate
 * connections so the assertion observes committed state rather than an open test transaction.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SlotControllerAtomicUpdateIT {

    private static final String HEADER = "X-User-Id";

    @Autowired private MockMvc mockMvc;

    @Autowired private DSLContext dsl;

    private UUID userId;
    private UUID calendarId;

    @AfterEach
    void cleanUp() {
        if (calendarId == null) {
            return;
        }
        dsl.deleteFrom(TimeSlots.TIME_SLOTS)
                .where(TimeSlots.TIME_SLOTS.CALENDAR_ID.eq(calendarId))
                .execute();
        dsl.deleteFrom(Calendars.CALENDARS).where(Calendars.CALENDARS.ID.eq(calendarId)).execute();
        dsl.deleteFrom(Users.USERS).where(Users.USERS.ID.eq(userId)).execute();
    }

    @Test
    void failedTimeChangeLeavesStatusUnchanged() throws Exception {
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30).withNano(0);
        seedCalendar();
        insertSlot(base, 60, SlotStatus.FREE);
        UUID target = insertSlot(base.plusMinutes(120), 30, SlotStatus.FREE);

        mockMvc.perform(
                        patch("/api/v1/slots/{slotId}", target)
                                .header(HEADER, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"status":"BUSY","startAt":"%s"}
                                """
                                                .formatted(base.plusMinutes(30))))
                .andExpect(status().isConflict());

        String response =
                mockMvc.perform(get("/api/v1/slots/{slotId}", target).header(HEADER, userId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("FREE"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        OffsetDateTime returnedStart = OffsetDateTime.parse(JsonPath.read(response, "$.startAt"));
        assertThat(returnedStart.toInstant()).isEqualTo(base.plusMinutes(120).toInstant());
    }

    private void seedCalendar() {
        userId = UUID.randomUUID();
        calendarId = UUID.randomUUID();
        dsl.insertInto(Users.USERS)
                .columns(Users.USERS.ID, Users.USERS.DISPLAY_NAME)
                .values(userId, "Atomic Update User")
                .execute();
        dsl.insertInto(Calendars.CALENDARS)
                .columns(Calendars.CALENDARS.ID, Calendars.CALENDARS.USER_ID)
                .values(calendarId, userId)
                .execute();
    }

    private UUID insertSlot(OffsetDateTime start, int durationMinutes, SlotStatus status) {
        UUID slotId = UUID.randomUUID();
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
                .values(slotId, calendarId, start, durationMinutes, status.name(), now, now)
                .execute();
        return slotId;
    }
}
