package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.application.port.out.CalendarRepository;
import com.minidoodle.calendar.domain.model.Calendar;
import com.minidoodle.support.PostgresIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The calendar row carries both created_at and updated_at; with no update operation yet the two are
 * equal on insert.
 */
class CalendarRepositoryIT extends PostgresIntegrationTest {

    @Autowired private CalendarRepository calendars;

    @Test
    void mapsCreatedAndUpdatedTimestamps() {
        Caller caller = createCaller();

        Calendar calendar = calendars.findByOwnerId(caller.userId()).orElseThrow();

        assertThat(calendar.id()).isEqualTo(caller.calendarId());
        assertThat(calendar.ownerId()).isEqualTo(caller.userId());
        assertThat(calendar.createdAt()).isNotNull();
        assertThat(calendar.updatedAt()).isNotNull();
        assertThat(calendar.updatedAt()).isEqualTo(calendar.createdAt());
    }
}
