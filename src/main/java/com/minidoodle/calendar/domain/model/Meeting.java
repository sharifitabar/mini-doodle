package com.minidoodle.calendar.domain.model;

import com.minidoodle.calendar.domain.exception.InvalidMeetingException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Meeting aggregate root. A meeting always belongs to exactly one {@link TimeSlot} (one meeting per
 * slot) and carries a title, an optional description, and one or more participants. Framework-free:
 * all invariants are enforced here.
 */
public final class Meeting {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    /**
     * Practical upper bound on participants. Persistence stores {@code position} as {@code
     * SMALLINT}; this limit stays well below that type's range and caps request cost.
     */
    public static final int MAX_PARTICIPANTS = 100;

    private final MeetingId id;
    private final SlotId slotId;
    private final String title;
    private final String description;
    private final List<Participant> participants;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    private Meeting(
            MeetingId id,
            SlotId slotId,
            String title,
            String description,
            List<Participant> participants,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.slotId = Objects.requireNonNull(slotId, "slotId must not be null");
        this.title = requireTitle(title);
        this.description = requireDescription(description);
        this.participants = requireParticipants(participants);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static Meeting create(
            SlotId slotId,
            String title,
            String description,
            List<Participant> participants,
            OffsetDateTime now) {
        Objects.requireNonNull(now, "now must not be null");
        // PostgreSQL stores timestamptz at microsecond resolution; truncate so the in-memory
        // aggregate equals what is read back (nanosecond clocks, e.g. on Linux, otherwise differ).
        OffsetDateTime utcNow =
                now.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        return new Meeting(
                MeetingId.random(), slotId, title, description, participants, utcNow, utcNow);
    }

    public static Meeting of(
            MeetingId id,
            SlotId slotId,
            String title,
            String description,
            List<Participant> participants,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        return new Meeting(id, slotId, title, description, participants, createdAt, updatedAt);
    }

    private static String requireTitle(String title) {
        if (title == null || title.strip().isEmpty()) {
            throw new InvalidMeetingException("Meeting title must not be blank");
        }
        String trimmed = title.strip();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new InvalidMeetingException(
                    "Meeting title must not exceed " + MAX_TITLE_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String requireDescription(String description) {
        if (description == null || description.strip().isEmpty()) {
            return null;
        }
        String trimmed = description.strip();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidMeetingException(
                    "Meeting description must not exceed "
                            + MAX_DESCRIPTION_LENGTH
                            + " characters");
        }
        return trimmed;
    }

    private static List<Participant> requireParticipants(List<Participant> participants) {
        if (participants == null || participants.isEmpty()) {
            throw new InvalidMeetingException("A meeting must have at least one participant");
        }
        if (participants.size() > MAX_PARTICIPANTS) {
            throw new InvalidMeetingException(
                    "A meeting must not have more than " + MAX_PARTICIPANTS + " participants");
        }
        if (participants.stream().anyMatch(Objects::isNull)) {
            throw new InvalidMeetingException("A participant must not be null");
        }
        return List.copyOf(participants);
    }

    public MeetingId id() {
        return id;
    }

    public SlotId slotId() {
        return slotId;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public List<Participant> participants() {
        return participants;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}
