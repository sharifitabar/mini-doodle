package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.domain.model.Meeting;
import com.minidoodle.calendar.domain.model.MeetingId;
import com.minidoodle.calendar.domain.model.Participant;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.jooq.tables.records.MeetingParticipantsRecord;
import com.minidoodle.jooq.tables.records.MeetingsRecord;

import java.util.List;

final class MeetingPersistenceMapper {

    private MeetingPersistenceMapper() {}

    static Meeting toMeeting(MeetingsRecord record, List<Participant> participants) {
        return Meeting.of(
                MeetingId.of(record.getId()),
                SlotId.of(record.getSlotId()),
                record.getTitle(),
                record.getDescription(),
                participants,
                record.getCreatedAt(),
                record.getUpdatedAt());
    }

    static Participant toParticipant(MeetingParticipantsRecord record) {
        return new Participant(record.getName(), record.getEmail());
    }
}
