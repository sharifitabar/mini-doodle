package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.application.port.out.MeetingRepository;
import com.minidoodle.calendar.domain.exception.MeetingConflictException;
import com.minidoodle.calendar.domain.model.Meeting;
import com.minidoodle.calendar.domain.model.Participant;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.jooq.tables.MeetingParticipants;
import com.minidoodle.jooq.tables.Meetings;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JooqMeetingRepository implements MeetingRepository {

    private static final String UNIQUE_VIOLATION = "23505";

    private final DSLContext dsl;

    JooqMeetingRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void save(Meeting meeting) {
        try {
            dsl.insertInto(Meetings.MEETINGS)
                    .columns(
                            Meetings.MEETINGS.ID,
                            Meetings.MEETINGS.SLOT_ID,
                            Meetings.MEETINGS.TITLE,
                            Meetings.MEETINGS.DESCRIPTION,
                            Meetings.MEETINGS.CREATED_AT,
                            Meetings.MEETINGS.UPDATED_AT)
                    .values(
                            meeting.id().value(),
                            meeting.slotId().value(),
                            meeting.title(),
                            meeting.description(),
                            meeting.createdAt(),
                            meeting.updatedAt())
                    .execute();
            insertParticipants(meeting);
        } catch (RuntimeException ex) {
            if (UNIQUE_VIOLATION.equals(sqlState(ex))) {
                throw new MeetingConflictException("Slot already has a meeting");
            }
            throw ex;
        }
    }

    private void insertParticipants(Meeting meeting) {
        List<Participant> participants = meeting.participants();
        List<Query> inserts = new ArrayList<>(participants.size());
        for (int position = 0; position < participants.size(); position++) {
            Participant participant = participants.get(position);
            inserts.add(dsl.insertInto(MeetingParticipants.MEETING_PARTICIPANTS)
                    .columns(
                            MeetingParticipants.MEETING_PARTICIPANTS.MEETING_ID,
                            MeetingParticipants.MEETING_PARTICIPANTS.POSITION,
                            MeetingParticipants.MEETING_PARTICIPANTS.NAME,
                            MeetingParticipants.MEETING_PARTICIPANTS.EMAIL)
                    .values(
                            meeting.id().value(),
                            (short) position,
                            participant.name(),
                            participant.email()));
        }
        dsl.batch(inserts).execute();
    }

    @Override
    public Optional<Meeting> findBySlotId(SlotId slotId) {
        return dsl.selectFrom(Meetings.MEETINGS)
                .where(Meetings.MEETINGS.SLOT_ID.eq(slotId.value()))
                .fetchOptional()
                .map(record -> MeetingPersistenceMapper.toMeeting(
                        record, loadParticipants(record.getId())));
    }

    @Override
    public boolean existsBySlotId(SlotId slotId) {
        return dsl.fetchExists(dsl.selectOne()
                .from(Meetings.MEETINGS)
                .where(Meetings.MEETINGS.SLOT_ID.eq(slotId.value())));
    }

    @Override
    public void deleteBySlotId(SlotId slotId) {
        dsl.deleteFrom(Meetings.MEETINGS)
                .where(Meetings.MEETINGS.SLOT_ID.eq(slotId.value()))
                .execute();
    }

    private List<Participant> loadParticipants(UUID meetingId) {
        return dsl.selectFrom(MeetingParticipants.MEETING_PARTICIPANTS)
                .where(MeetingParticipants.MEETING_PARTICIPANTS.MEETING_ID.eq(meetingId))
                .orderBy(MeetingParticipants.MEETING_PARTICIPANTS.POSITION.asc())
                .fetch(MeetingPersistenceMapper::toParticipant);
    }

    private String sqlState(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
        }
        return null;
    }
}
