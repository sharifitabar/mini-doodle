package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.calendar.application.port.in.BookMeetingCommand;
import com.minidoodle.calendar.application.port.in.MeetingView;
import com.minidoodle.calendar.domain.model.Meeting;
import com.minidoodle.calendar.domain.model.Participant;
import com.minidoodle.calendar.domain.model.SlotId;

import java.util.List;

final class MeetingWebMapper {

    private MeetingWebMapper() {}

    static BookMeetingCommand toCommand(SlotId slotId, BookMeetingRequest request) {
        List<Participant> participants =
                request.participants().stream()
                        .map(
                                participant ->
                                        new Participant(participant.name(), participant.email()))
                        .toList();
        return new BookMeetingCommand(slotId, request.title(), request.description(), participants);
    }

    static MeetingResponse toResponse(MeetingView view) {
        Meeting meeting = view.meeting();
        return new MeetingResponse(
                meeting.slotId().value(),
                meeting.title(),
                meeting.description(),
                meeting.participants().stream()
                        .map(
                                participant ->
                                        new ParticipantResponse(
                                                participant.name(), participant.email()))
                        .toList(),
                view.slotTimeRange().start(),
                view.slotTimeRange().end(),
                meeting.createdAt(),
                meeting.updatedAt());
    }
}
