package com.minidoodle.calendar.application.service;

import com.minidoodle.calendar.application.port.in.BookMeetingCommand;
import com.minidoodle.calendar.application.port.in.BookMeetingUseCase;
import com.minidoodle.calendar.application.port.in.CancelMeetingUseCase;
import com.minidoodle.calendar.application.port.in.DeleteSlotUseCase;
import com.minidoodle.calendar.application.port.in.GetMeetingUseCase;
import com.minidoodle.calendar.application.port.in.GetSlotUseCase;
import com.minidoodle.calendar.application.port.in.UpdateSlotCommand;
import com.minidoodle.calendar.application.port.in.UpdateSlotUseCase;
import com.minidoodle.calendar.domain.exception.MeetingConflictException;
import com.minidoodle.calendar.domain.exception.SlotNotFoundException;
import com.minidoodle.calendar.domain.model.MeetingId;
import com.minidoodle.calendar.domain.model.Participant;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.UserId;
import com.minidoodle.jooq.tables.Calendars;
import com.minidoodle.jooq.tables.Meetings;
import com.minidoodle.jooq.tables.TimeSlots;
import com.minidoodle.jooq.tables.Users;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Exercises real use-case transactions and verifies competing workflows wait on the slot lock. */
@SpringBootTest
@ActiveProfiles("test")
class BookingWorkflowConcurrencyIT {
    @Autowired private DSLContext dsl;
    @Autowired private BookMeetingUseCase book;
    @Autowired private CancelMeetingUseCase cancel;
    @Autowired private UpdateSlotUseCase update;
    @Autowired private DeleteSlotUseCase delete;
    @Autowired private GetSlotUseCase get;
    @Autowired private GetMeetingUseCase getMeeting;
    @Autowired private PlatformTransactionManager transactions;
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
    void bookingBlocksAnEditAndTheEditIsRejectedAfterCommit() throws Exception {
        SlotId id = seedSlot();
        Throwable failure = afterLocked(() -> book(id), () -> edit(id));
        assertThat(failure).isInstanceOf(MeetingConflictException.class);
        assertThat(get.getSlot(caller(), id).timeRange().durationMinutes()).isEqualTo(60);
        assertThat(get.getSlot(caller(), id).status()).isEqualTo(SlotStatus.BUSY);
        assertThat(getMeeting.getMeeting(caller(), id).meeting().participants()).hasSize(1);
    }

    @Test
    void bookingWaitsForAnEditAndUsesTheCommittedTimeRange() throws Exception {
        SlotId id = seedSlot();
        assertThat(afterLocked(() -> edit(id), () -> book(id))).isNull();
        assertThat(getMeeting.getMeeting(caller(), id).slotTimeRange().durationMinutes())
                .isEqualTo(45);
        assertThat(get.getSlot(caller(), id).status()).isEqualTo(SlotStatus.BUSY);
    }

    @Test
    void bookingWaitsForCancellationAndCanBookTheReleasedSlot() throws Exception {
        SlotId id = seedSlot();
        book(id);
        MeetingId original = getMeeting.getMeeting(caller(), id).meeting().id();
        assertThat(afterLocked(() -> cancel.cancelMeeting(caller(), id), () -> book(id))).isNull();
        assertThat(getMeeting.getMeeting(caller(), id).meeting().id()).isNotEqualTo(original);
        assertThat(get.getSlot(caller(), id).status()).isEqualTo(SlotStatus.BUSY);
    }

    @Test
    void deletionWaitsForBookingAndIsRejectedAfterCommit() throws Exception {
        SlotId id = seedSlot();
        assertThat(afterLocked(() -> book(id), () -> delete.deleteSlot(caller(), id)))
                .isInstanceOf(MeetingConflictException.class);
        assertThat(getMeeting.getMeeting(caller(), id).meeting().slotId()).isEqualTo(id);
    }

    @Test
    void bookingWaitsForDeletionAndSeesTheSlotIsGone() throws Exception {
        SlotId id = seedSlot();
        assertThat(afterLocked(() -> delete.deleteSlot(caller(), id), () -> book(id)))
                .isInstanceOf(SlotNotFoundException.class);
        assertThat(dsl.fetchCount(TimeSlots.TIME_SLOTS, TimeSlots.TIME_SLOTS.ID.eq(id.value())))
                .isZero();
        assertThat(dsl.fetchCount(Meetings.MEETINGS, Meetings.MEETINGS.SLOT_ID.eq(id.value())))
                .isZero();
    }

    @Test
    void secondBookingWaitsAndIsRejectedAfterTheFirstCommits() throws Exception {
        SlotId id = seedSlot();
        assertThat(afterLocked(() -> book(id), () -> book(id)))
                .isInstanceOf(MeetingConflictException.class);
        assertThat(dsl.fetchCount(Meetings.MEETINGS, Meetings.MEETINGS.SLOT_ID.eq(id.value())))
                .isEqualTo(1);
    }

    @Test
    void editWaitsForCancellationAndUpdatesTheReleasedSlot() throws Exception {
        SlotId id = seedSlot();
        book(id);
        assertThat(afterLocked(() -> cancel.cancelMeeting(caller(), id), () -> edit(id))).isNull();
        assertThat(get.getSlot(caller(), id).status()).isEqualTo(SlotStatus.FREE);
        assertThat(get.getSlot(caller(), id).timeRange().durationMinutes()).isEqualTo(45);
        assertThat(dsl.fetchCount(Meetings.MEETINGS, Meetings.MEETINGS.SLOT_ID.eq(id.value())))
                .isZero();
    }

    private Throwable afterLocked(Runnable first, Runnable second) throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        AtomicReference<Future<Throwable>> result = new AtomicReference<>();
        try {
            new TransactionTemplate(transactions)
                    .executeWithoutResult(
                            transaction -> {
                                Integer owner =
                                        dsl.fetchOne("select pg_backend_pid()")
                                                .get(0, Integer.class);
                                first.run();
                                result.set(worker.submit(() -> attempt(second)));
                                await().atMost(Duration.ofSeconds(10))
                                        .until(
                                                () ->
                                                        Boolean.TRUE.equals(
                                                                dsl.fetchValue(
                                                                        "select exists(select 1 from pg_stat_activity where ? = any(pg_blocking_pids(pid)))",
                                                                        owner)));
                            });
            return result.get().get(10, TimeUnit.SECONDS);
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Throwable attempt(Runnable operation) {
        try {
            operation.run();
            return null;
        } catch (RuntimeException failure) {
            return failure;
        }
    }

    private UserId caller() {
        return UserId.of(userId);
    }

    private void book(SlotId id) {
        book.bookMeeting(
                caller(),
                new BookMeetingCommand(
                        id, "Planning", null, List.of(new Participant("Ada", "ada@example.com"))));
    }

    private void edit(SlotId id) {
        update.updateSlot(caller(), id, new UpdateSlotCommand(null, null, 45));
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
}
