package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.exception.SlotOverlapException;
import com.minidoodle.calendar.domain.model.TimeRange;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlotRepositoryModifyIT extends PostgresIntegrationTest {

    @Autowired
    private SlotRepository slots;

    @Test
    void persistsNonOverlappingUpdate() {
        Caller caller = createCaller();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime start = now.plusDays(1).withNano(0);

        TimeSlot host = TimeSlot.create(caller.calendarId(), new TimeRange(start, 60), now);
        slots.insert(host);
        TimeSlot target = TimeSlot.create(
                caller.calendarId(), new TimeRange(start.plusMinutes(120), 30), now);
        slots.insert(target);

        // Move target to start exactly when host ends: half-open ranges stay disjoint.
        target.changeTime(new TimeRange(start.plusMinutes(60), 30), now);
        assertThatCode(() -> slots.update(target)).doesNotThrowAnyException();

        TimeSlot reloaded = slots.findByIdAndCalendarId(target.id(), caller.calendarId()).orElseThrow();
        assertThat(reloaded.timeRange().start()).isEqualTo(start.plusMinutes(60));
        assertThat(reloaded.timeRange().durationMinutes()).isEqualTo(30);
    }

    @Test
    void rejectsUpdateIntoOverlappingRange() {
        Caller caller = createCaller();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime start = now.plusDays(1).withNano(0);

        slots.insert(TimeSlot.create(caller.calendarId(), new TimeRange(start, 60), now));
        TimeSlot target = TimeSlot.create(
                caller.calendarId(), new TimeRange(start.plusMinutes(120), 30), now);
        slots.insert(target);

        target.changeTime(new TimeRange(start.plusMinutes(30), 30), now);

        assertThatThrownBy(() -> slots.update(target))
                .isInstanceOf(SlotOverlapException.class);
    }
}
