package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.domain.exception.MeetingConflictException;
import com.minidoodle.calendar.domain.model.Meeting;
import com.minidoodle.calendar.domain.model.Participant;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.jooq.tables.Calendars;
import com.minidoodle.jooq.tables.Meetings;
import com.minidoodle.jooq.tables.TimeSlots;
import com.minidoodle.jooq.tables.Users;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Genuine concurrency: two bookings for the same slot race against the {@code uq_meetings_slot}
 * unique constraint. Exactly one must win; the other must be rejected as a conflict.
 *
 * <p>Not transactional on purpose — the inserts must commit for the constraint to arbitrate.
 */
@SpringBootTest
@ActiveProfiles("test")
class MeetingBookingConcurrencyIT {

    @Autowired private DSLContext dsl;

    @Autowired private JooqMeetingRepository meetings;

    private UUID userId;
    private UUID calendarId;

    @AfterEach
    void cleanUp() {
        if (calendarId == null) {
            return;
        }
        dsl.deleteFrom(Meetings.MEETINGS)
                .where(
                        Meetings.MEETINGS.SLOT_ID.in(
                                dsl.select(TimeSlots.TIME_SLOTS.ID)
                                        .from(TimeSlots.TIME_SLOTS)
                                        .where(TimeSlots.TIME_SLOTS.CALENDAR_ID.eq(calendarId))))
                .execute();
        dsl.deleteFrom(TimeSlots.TIME_SLOTS)
                .where(TimeSlots.TIME_SLOTS.CALENDAR_ID.eq(calendarId))
                .execute();
        dsl.deleteFrom(Calendars.CALENDARS).where(Calendars.CALENDARS.ID.eq(calendarId)).execute();
        dsl.deleteFrom(Users.USERS).where(Users.USERS.ID.eq(userId)).execute();
    }

    @Test
    void secondSaveForSameSlotIsRejected() {
        SlotId slotId = seedSlot();

        meetings.save(meeting(slotId));

        assertThatThrownBy(() -> meetings.save(meeting(slotId)))
                .isInstanceOf(MeetingConflictException.class);
    }

    @Test
    void onlyOneOfTwoConcurrentBookingsSucceeds() throws Exception {
        SlotId slotId = seedSlot();
        Meeting first = meeting(slotId);
        Meeting second = meeting(slotId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();
        for (Meeting meeting : List.of(first, second)) {
            futures.add(
                    pool.submit(
                            (Callable<Throwable>)
                                    () -> {
                                        ready.countDown();
                                        go.await();
                                        try {
                                            meetings.save(meeting);
                                            return null;
                                        } catch (Throwable error) {
                                            return error;
                                        }
                                    }));
        }

        ready.await();
        go.countDown();

        int successes = 0;
        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> future : futures) {
            Throwable error = future.get();
            if (error == null) {
                successes++;
            } else {
                failures.add(error);
            }
        }
        pool.shutdownNow();

        assertThat(successes).isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(MeetingConflictException.class);
    }

    private SlotId seedSlot() {
        userId = UUID.randomUUID();
        calendarId = UUID.randomUUID();
        dsl.insertInto(Users.USERS)
                .columns(Users.USERS.ID, Users.USERS.DISPLAY_NAME)
                .values(userId, "Concurrency User")
                .execute();
        dsl.insertInto(Calendars.CALENDARS)
                .columns(Calendars.CALENDARS.ID, Calendars.CALENDARS.USER_ID)
                .values(calendarId, userId)
                .execute();

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
                .values(slotId, calendarId, now.plusDays(1), 60, SlotStatus.FREE.name(), now, now)
                .execute();
        return SlotId.of(slotId);
    }

    private Meeting meeting(SlotId slotId) {
        return Meeting.create(
                slotId,
                "Sprint planning",
                null,
                List.of(new Participant("Ada", "ada@example.com")),
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
