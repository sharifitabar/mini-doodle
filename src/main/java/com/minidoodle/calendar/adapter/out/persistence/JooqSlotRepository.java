package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.application.port.out.SlotFilter;
import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.exception.MeetingConflictException;
import com.minidoodle.calendar.domain.exception.SlotOverlapException;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.jooq.tables.TimeSlots;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
class JooqSlotRepository implements SlotRepository {

    private static final String EXCLUSION_VIOLATION = "23P01";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String DEADLOCK_DETECTED = "40P01";
    private static final String SERIALIZATION_FAILURE = "40001";
    private static final int MAX_ATTEMPTS = 6;
    private static final long RETRY_BACKOFF_MILLIS = 10L;

    private final DSLContext dsl;

    JooqSlotRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void insert(TimeSlot slot) {
        execute(() -> insertInternal(slot));
    }

    private void insertInternal(TimeSlot slot) {
        dsl.insertInto(TimeSlots.TIME_SLOTS)
                .columns(
                        TimeSlots.TIME_SLOTS.ID,
                        TimeSlots.TIME_SLOTS.CALENDAR_ID,
                        TimeSlots.TIME_SLOTS.START_AT,
                        TimeSlots.TIME_SLOTS.DURATION_MINUTES,
                        TimeSlots.TIME_SLOTS.STATUS,
                        TimeSlots.TIME_SLOTS.CREATED_AT,
                        TimeSlots.TIME_SLOTS.UPDATED_AT)
                .values(
                        slot.id().value(),
                        slot.calendarId().value(),
                        slot.timeRange().start(),
                        slot.timeRange().durationMinutes(),
                        slot.status().name(),
                        slot.createdAt(),
                        slot.updatedAt())
                .execute();
    }

    @Override
    public void update(TimeSlot slot) {
        execute(() -> dsl.update(TimeSlots.TIME_SLOTS)
                .set(TimeSlots.TIME_SLOTS.START_AT, slot.timeRange().start())
                .set(TimeSlots.TIME_SLOTS.DURATION_MINUTES, slot.timeRange().durationMinutes())
                .set(TimeSlots.TIME_SLOTS.STATUS, slot.status().name())
                .set(TimeSlots.TIME_SLOTS.UPDATED_AT, slot.updatedAt())
                .where(TimeSlots.TIME_SLOTS.ID.eq(slot.id().value())
                        .and(TimeSlots.TIME_SLOTS.CALENDAR_ID.eq(slot.calendarId().value())))
                .execute());
    }

    @Override
    public void deleteByIdAndCalendarId(SlotId slotId, CalendarId calendarId) {
        try {
            dsl.deleteFrom(TimeSlots.TIME_SLOTS)
                    .where(TimeSlots.TIME_SLOTS.ID.eq(slotId.value())
                            .and(TimeSlots.TIME_SLOTS.CALENDAR_ID.eq(calendarId.value())))
                    .execute();
        } catch (RuntimeException ex) {
            if (FOREIGN_KEY_VIOLATION.equals(sqlState(ex))) {
                throw new MeetingConflictException("Slot cannot be deleted while it holds a meeting");
            }
            throw ex;
        }
    }

    @Override
    public Optional<TimeSlot> findByIdAndCalendarId(SlotId slotId, CalendarId calendarId) {
        return dsl.selectFrom(TimeSlots.TIME_SLOTS)
                .where(TimeSlots.TIME_SLOTS.ID.eq(slotId.value())
                        .and(TimeSlots.TIME_SLOTS.CALENDAR_ID.eq(calendarId.value())))
                .fetchOptional(PersistenceMapper::toTimeSlot);
    }

    @Override
    public List<TimeSlot> find(SlotFilter filter) {
        return dsl.selectFrom(TimeSlots.TIME_SLOTS)
                .where(conditions(filter))
                .orderBy(TimeSlots.TIME_SLOTS.START_AT.asc(), TimeSlots.TIME_SLOTS.ID.asc())
                .limit(filter.limit())
                .offset(filter.offset())
                .fetch(PersistenceMapper::toTimeSlot);
    }

    @Override
    public long count(SlotFilter filter) {
        return dsl.fetchCount(dsl.selectFrom(TimeSlots.TIME_SLOTS).where(conditions(filter)));
    }

    private List<Condition> conditions(SlotFilter filter) {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(TimeSlots.TIME_SLOTS.CALENDAR_ID.eq(filter.calendarId().value()));
        if (filter.status() != null) {
            conditions.add(TimeSlots.TIME_SLOTS.STATUS.eq(filter.status().name()));
        }
        if (filter.from() != null || filter.to() != null) {
            conditions.add(overlapsWindow(filter.from(), filter.to()));
        }
        return conditions;
    }

    /**
     * Overlap predicate against the half-open window {@code [from, to)}. Either bound
     * may be absent (unbounded). Uses the generated {@code slot_range} column so the
     * GIST index can serve the predicate; jOOQ OSS has no range type, so plain SQL is used.
     */
    private Condition overlapsWindow(OffsetDateTime from, OffsetDateTime to) {
        StringBuilder sql = new StringBuilder("slot_range && tstzrange(");
        List<Object> bindings = new ArrayList<>();
        if (from != null) {
            sql.append('{').append(bindings.size()).append('}');
            bindings.add(from);
        } else {
            sql.append("'-infinity'::timestamptz");
        }
        sql.append(", ");
        if (to != null) {
            sql.append('{').append(bindings.size()).append('}');
            bindings.add(to);
        } else {
            sql.append("'infinity'::timestamptz");
        }
        sql.append(", '[)')");
        return DSL.condition(sql.toString(), bindings.toArray());
    }

    /**
     * The database exclusion constraint ({@code ex_slot_no_overlap}) is the authoritative
     * guard against overlapping slots. When it fires, a domain-level overlap error (409) is
     * surfaced instead of a raw persistence exception. Concurrent inserts into a GiST
     * exclusion constraint can also fail with a deadlock or serialization failure; such
     * transient conflicts are retried a bounded number of times so the write converges on the
     * definitive exclusion violation once the winning transaction commits. The SQL error may
     * be surfaced as a jOOQ exception or wrapped as a Spring {@code DataAccessException}, so
     * the whole cause chain is inspected.
     */
    private void execute(Runnable action) {
        for (int attempt = 1; ; attempt++) {
            try {
                action.run();
                return;
            } catch (RuntimeException ex) {
                String sqlState = sqlState(ex);
                if (EXCLUSION_VIOLATION.equals(sqlState)) {
                    throw new SlotOverlapException("Slot overlaps an existing slot in the calendar");
                }
                boolean transientConflict = DEADLOCK_DETECTED.equals(sqlState)
                        || SERIALIZATION_FAILURE.equals(sqlState);
                if (transientConflict && attempt < MAX_ATTEMPTS) {
                    backoff(attempt);
                    continue;
                }
                throw ex;
            }
        }
    }

    private String sqlState(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
        }
        return null;
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(RETRY_BACKOFF_MILLIS * attempt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying slot write", interrupted);
        }
    }
}
