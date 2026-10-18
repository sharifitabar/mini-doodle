package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.application.port.out.SlotFilter;
import com.minidoodle.calendar.application.port.out.SlotRepository;
import com.minidoodle.calendar.domain.exception.MeetingConflictException;
import com.minidoodle.calendar.domain.model.CalendarId;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.jooq.tables.TimeSlots;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
class JooqSlotRepository implements SlotRepository {

    private static final String FOREIGN_KEY_VIOLATION = "23503";

    private final DSLContext dsl;

    /**
     * The exclusion-constraint defence is shared by every write here; its behaviour (retry on
     * transient conflicts, map the definitive exclusion violation to a domain error) is documented
     * on {@link SlotWriteRetry}.
     */
    private final SlotWriteRetry writeRetry = new SlotWriteRetry();

    JooqSlotRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void insert(TimeSlot slot) {
        writeRetry.run(() -> insertInternal(slot));
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
        writeRetry.run(
                () ->
                        dsl.update(TimeSlots.TIME_SLOTS)
                                .set(TimeSlots.TIME_SLOTS.START_AT, slot.timeRange().start())
                                .set(
                                        TimeSlots.TIME_SLOTS.DURATION_MINUTES,
                                        slot.timeRange().durationMinutes())
                                .set(TimeSlots.TIME_SLOTS.STATUS, slot.status().name())
                                .set(TimeSlots.TIME_SLOTS.UPDATED_AT, slot.updatedAt())
                                .where(
                                        TimeSlots.TIME_SLOTS
                                                .ID
                                                .eq(slot.id().value())
                                                .and(
                                                        TimeSlots.TIME_SLOTS.CALENDAR_ID.eq(
                                                                slot.calendarId().value())))
                                .execute());
    }

    @Override
    public void deleteByIdAndCalendarId(SlotId slotId, CalendarId calendarId) {
        try {
            dsl.deleteFrom(TimeSlots.TIME_SLOTS)
                    .where(
                            TimeSlots.TIME_SLOTS
                                    .ID
                                    .eq(slotId.value())
                                    .and(TimeSlots.TIME_SLOTS.CALENDAR_ID.eq(calendarId.value())))
                    .execute();
        } catch (RuntimeException ex) {
            if (FOREIGN_KEY_VIOLATION.equals(SlotWriteRetry.sqlState(ex))) {
                throw new MeetingConflictException(
                        "Slot cannot be deleted while it holds a meeting");
            }
            throw ex;
        }
    }

    @Override
    public Optional<TimeSlot> findByIdAndCalendarId(SlotId slotId, CalendarId calendarId) {
        return dsl.selectFrom(TimeSlots.TIME_SLOTS)
                .where(
                        TimeSlots.TIME_SLOTS
                                .ID
                                .eq(slotId.value())
                                .and(TimeSlots.TIME_SLOTS.CALENDAR_ID.eq(calendarId.value())))
                .fetchOptional(PersistenceMapper::toTimeSlot);
    }

    @Override
    public Optional<TimeSlot> findByIdAndCalendarIdForUpdate(SlotId slotId, CalendarId calendarId) {
        return dsl.selectFrom(TimeSlots.TIME_SLOTS)
                .where(
                        TimeSlots.TIME_SLOTS
                                .ID
                                .eq(slotId.value())
                                .and(TimeSlots.TIME_SLOTS.CALENDAR_ID.eq(calendarId.value())))
                .forUpdate()
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
     * Overlap predicate against the half-open window {@code [from, to)}. Either bound may be absent
     * (unbounded). Uses the generated {@code slot_range} column so the GIST index can serve the
     * predicate; jOOQ OSS has no range type, so plain SQL is used.
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
}
