package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.exception.SlotOverlapException;
import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.TimeRange;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.jooq.tables.Calendars;
import com.minidoodle.jooq.tables.TimeSlots;
import com.minidoodle.jooq.tables.Users;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Genuine concurrency: many mutually overlapping inserts race against the database
 * exclusion constraint. Exactly one must win; the rest must be rejected as overlaps.
 *
 * <p>Not transactional on purpose — the inserts must commit for the constraint to
 * arbitrate between concurrent connections.
 */
@SpringBootTest
@ActiveProfiles("test")
class SlotRepositoryConcurrencyIT {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private SlotRepository slots;

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
        dsl.deleteFrom(Calendars.CALENDARS)
                .where(Calendars.CALENDARS.ID.eq(calendarId))
                .execute();
        dsl.deleteFrom(Users.USERS)
                .where(Users.USERS.ID.eq(userId))
                .execute();
    }

    @Test
    void onlyOneOfManyConcurrentOverlappingInsertsSucceeds() throws Exception {
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

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime base = now.plusDays(1).withNano(0);
        int attempts = 8;
        List<TimeSlot> candidates = IntStream.range(0, attempts)
                .mapToObj(i -> TimeSlot.create(
                        CalendarId.of(calendarId), new TimeRange(base.plusMinutes(i % 4), 60), now))
                .toList();

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();
        for (TimeSlot candidate : candidates) {
            futures.add(pool.submit((Callable<Throwable>) () -> {
                ready.countDown();
                go.await();
                try {
                    slots.insert(candidate);
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
        assertThat(failures).hasSize(attempts - 1);
        assertThat(failures).allSatisfy(
                error -> assertThat(error).isInstanceOf(SlotOverlapException.class));
    }
}
