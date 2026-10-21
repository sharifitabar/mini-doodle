package com.minidoodle.calendar.adapter.out.outbox;

import com.minidoodle.calendar.application.port.in.BookMeetingCommand;
import com.minidoodle.calendar.application.port.in.BookMeetingUseCase;
import com.minidoodle.calendar.application.port.in.CancelMeetingUseCase;
import com.minidoodle.calendar.application.port.in.CreateSlotCommand;
import com.minidoodle.calendar.application.port.in.CreateSlotUseCase;
import com.minidoodle.calendar.application.port.in.DeleteSlotUseCase;
import com.minidoodle.calendar.application.port.in.GetSlotUseCase;
import com.minidoodle.calendar.application.port.in.UpdateSlotCommand;
import com.minidoodle.calendar.application.port.in.UpdateSlotUseCase;
import com.minidoodle.calendar.domain.exception.MeetingConflictException;
import com.minidoodle.calendar.domain.model.Participant;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.jooq.tables.OutboxEvents;
import com.minidoodle.jooq.tables.TimeSlots;
import com.minidoodle.jooq.tables.records.OutboxEventsRecord;
import com.minidoodle.support.PostgresIntegrationTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Capture runs in the transaction of the use case. The test is transactional, so a failure of an
 * operation leaves no row, and a rolled-back outer transaction leaves no row either.
 */
class OutboxCaptureIT extends PostgresIntegrationTest {

    @Autowired private CreateSlotUseCase createSlot;
    @Autowired private UpdateSlotUseCase updateSlot;
    @Autowired private DeleteSlotUseCase deleteSlot;
    @Autowired private BookMeetingUseCase bookMeeting;
    @Autowired private CancelMeetingUseCase cancelMeeting;
    @Autowired private GetSlotUseCase getSlot;
    @Autowired private PlatformTransactionManager transactionManager;

    private TimeSlot newSlot(Caller caller) {
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2);
        return createSlot.createSlot(caller.userId(), new CreateSlotCommand(start, 30));
    }

    private List<OutboxEventsRecord> rows(Caller caller) {
        return dsl.selectFrom(OutboxEvents.OUTBOX_EVENTS)
                .where(OutboxEvents.OUTBOX_EVENTS.CALENDAR_ID.eq(caller.calendarId().value()))
                .orderBy(OutboxEvents.OUTBOX_EVENTS.ID.asc())
                .fetch();
    }

    private BookMeetingCommand booking(SlotId slotId) {
        return new BookMeetingCommand(
                slotId, "Planning", "Details", List.of(new Participant("P0", "p0@example.com")));
    }

    @Test
    void createWritesOneSlotCreatedRowWithTheEnvelope() {
        Caller caller = createCaller();

        TimeSlot slot = newSlot(caller);

        List<OutboxEventsRecord> rows = rows(caller);
        assertThat(rows).hasSize(1);
        OutboxEventsRecord row = rows.get(0);
        assertThat(row.getEventType()).isEqualTo("slot.created");
        assertThat(row.getSchemaVersion()).isEqualTo(1);
        assertThat(row.getAggregateType()).isEqualTo("slot");
        assertThat(row.getAggregateId()).isEqualTo(slot.id().value());
        assertThat(row.getCalendarId()).isEqualTo(caller.calendarId().value());
        assertThat(row.getState()).isEqualTo("PENDING");
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getPayload().data()).contains("start_at").contains("FREE");
    }

    @Test
    void bookingWritesOneMeetingBookedRowWithoutParticipantDetail() {
        Caller caller = createCaller();
        TimeSlot slot = newSlot(caller);

        bookMeeting.bookMeeting(caller.userId(), booking(slot.id()));

        List<OutboxEventsRecord> rows = rows(caller);
        assertThat(rows).hasSize(2);
        OutboxEventsRecord row = rows.get(1);
        assertThat(row.getEventType()).isEqualTo("meeting.booked");
        assertThat(row.getAggregateId()).isEqualTo(slot.id().value());
        assertThat(row.getPayload().data())
                .contains("Planning")
                .contains("participant_count")
                .doesNotContain("p0@example.com")
                .doesNotContain("\"name\"");
    }

    @Test
    void cancelWritesOneMeetingCancelledRow() {
        Caller caller = createCaller();
        TimeSlot slot = newSlot(caller);
        bookMeeting.bookMeeting(caller.userId(), booking(slot.id()));

        cancelMeeting.cancelMeeting(caller.userId(), slot.id());

        List<OutboxEventsRecord> rows = rows(caller);
        assertThat(rows).hasSize(3);
        assertThat(rows.get(2).getEventType()).isEqualTo("meeting.cancelled");
    }

    @Test
    void anUpdateWritesOneSlotUpdatedRow() {
        Caller caller = createCaller();
        TimeSlot slot = newSlot(caller);

        updateSlot.updateSlot(
                caller.userId(), slot.id(), new UpdateSlotCommand(SlotStatus.BUSY, null, null));

        List<OutboxEventsRecord> rows = rows(caller);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).getEventType()).isEqualTo("slot.updated");
        assertThat(rows.get(1).getPayload().data()).contains("status").contains("BUSY");
    }

    @Test
    void aNoOpUpdateWritesNoRow() {
        Caller caller = createCaller();
        TimeSlot slot = newSlot(caller);

        updateSlot.updateSlot(
                caller.userId(), slot.id(), new UpdateSlotCommand(SlotStatus.FREE, null, null));

        assertThat(rows(caller)).hasSize(1);
    }

    @Test
    void aFailedBookingWritesNoRow() {
        Caller caller = createCaller();
        TimeSlot slot = newSlot(caller);
        bookMeeting.bookMeeting(caller.userId(), booking(slot.id()));

        assertThatThrownBy(() -> bookMeeting.bookMeeting(caller.userId(), booking(slot.id())))
                .isInstanceOf(MeetingConflictException.class);

        assertThat(rows(caller)).hasSize(2);
    }

    @Test
    void aDeleteWithAMeetingWritesNoRow() {
        Caller caller = createCaller();
        TimeSlot slot = newSlot(caller);
        bookMeeting.bookMeeting(caller.userId(), booking(slot.id()));

        assertThatThrownBy(() -> deleteSlot.deleteSlot(caller.userId(), slot.id()))
                .isInstanceOf(MeetingConflictException.class);

        assertThat(rows(caller)).hasSize(2);
    }

    @Test
    void aDeleteWritesOneSlotDeletedRow() {
        Caller caller = createCaller();
        TimeSlot slot = newSlot(caller);

        deleteSlot.deleteSlot(caller.userId(), slot.id());

        List<OutboxEventsRecord> rows = rows(caller);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).getEventType()).isEqualTo("slot.deleted");
    }

    @Test
    void aReadWritesNoRow() {
        Caller caller = createCaller();
        TimeSlot slot = newSlot(caller);

        getSlot.getSlot(caller.userId(), slot.id());

        assertThat(rows(caller)).hasSize(1);
    }

    @Test
    void aRolledBackTransactionKeepsNeitherTheSlotNorTheEvent() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        UUID[] slotId = new UUID[1];
        UUID[] calendarId = new UUID[1];

        template.executeWithoutResult(
                status -> {
                    Caller caller = createCaller();
                    calendarId[0] = caller.calendarId().value();
                    slotId[0] = newSlot(caller).id().value();
                    status.setRollbackOnly();
                });

        assertThat(dsl.fetchCount(TimeSlots.TIME_SLOTS, TimeSlots.TIME_SLOTS.ID.eq(slotId[0])))
                .isZero();
        assertThat(
                        dsl.fetchCount(
                                OutboxEvents.OUTBOX_EVENTS,
                                OutboxEvents.OUTBOX_EVENTS.CALENDAR_ID.eq(calendarId[0])))
                .isZero();
    }
}
