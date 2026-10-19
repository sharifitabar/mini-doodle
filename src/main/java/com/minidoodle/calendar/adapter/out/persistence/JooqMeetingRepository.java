package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.domain.exception.MeetingConflictException;
import com.minidoodle.calendar.domain.model.Meeting;
import com.minidoodle.calendar.domain.model.Participant;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.jooq.tables.MeetingParticipants;
import com.minidoodle.jooq.tables.Meetings;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Query;
import org.springframework.stereotype.Repository;

@Repository
class JooqMeetingRepository {

    private static final String UNIQUE_VIOLATION = "23505";

    private final DSLContext dsl;

    JooqMeetingRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

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
            inserts.add(
                    dsl.insertInto(MeetingParticipants.MEETING_PARTICIPANTS)
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

    public Optional<Meeting> findBySlotId(SlotId slotId) {
        return dsl.selectFrom(Meetings.MEETINGS)
                .where(Meetings.MEETINGS.SLOT_ID.eq(slotId.value()))
                .fetchOptional()
                .map(
                        record ->
                                MeetingPersistenceMapper.toMeeting(
                                        record, loadParticipants(record.getId())));
    }

    /** Loads child entities in batches so slot listing does not introduce N+1 queries. */
    java.util.Map<SlotId, Meeting> findBySlotIds(List<SlotId> ids) {
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        var records =
                dsl.selectFrom(Meetings.MEETINGS)
                        .where(
                                Meetings.MEETINGS.SLOT_ID.in(
                                        ids.stream().map(SlotId::value).toList()))
                        .fetch();
        var participants =
                dsl.selectFrom(MeetingParticipants.MEETING_PARTICIPANTS)
                        .where(
                                MeetingParticipants.MEETING_PARTICIPANTS.MEETING_ID.in(
                                        records.stream().map(record -> record.getId()).toList()))
                        .orderBy(MeetingParticipants.MEETING_PARTICIPANTS.POSITION)
                        .fetchGroups(
                                MeetingParticipants.MEETING_PARTICIPANTS.MEETING_ID,
                                MeetingPersistenceMapper::toParticipant);
        java.util.Map<SlotId, Meeting> result = new java.util.HashMap<>();
        for (var record : records) {
            List<Participant> values = participants.getOrDefault(record.getId(), List.of());
            result.put(
                    SlotId.of(record.getSlotId()),
                    MeetingPersistenceMapper.toMeeting(record, values));
        }
        return result;
    }

    /**
     * Persists changes to the child identity, preserving unchanged meetings and participant rows.
     */
    void synchronize(TimeSlot slot) {
        var stored =
                dsl.select(Meetings.MEETINGS.ID)
                        .from(Meetings.MEETINGS)
                        .where(Meetings.MEETINGS.SLOT_ID.eq(slot.id().value()))
                        .fetchOptional(Meetings.MEETINGS.ID);
        var desired = slot.findMeeting().map(meeting -> meeting.id().value());
        if (!stored.equals(desired)) {
            deleteBySlotId(slot.id());
            slot.findMeeting().ifPresent(this::save);
        }
    }

    public boolean existsBySlotId(SlotId slotId) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(Meetings.MEETINGS)
                        .where(Meetings.MEETINGS.SLOT_ID.eq(slotId.value())));
    }

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
