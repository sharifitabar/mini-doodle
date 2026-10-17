package com.minidoodle.calendar.adapter.out.persistence;

import com.minidoodle.calendar.application.port.out.CalendarRepository;
import com.minidoodle.calendar.domain.model.Calendar;
import com.minidoodle.calendar.domain.model.UserId;
import com.minidoodle.jooq.tables.Calendars;

import java.util.Optional;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
class JooqCalendarRepository implements CalendarRepository {

    private final DSLContext dsl;

    JooqCalendarRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<Calendar> findByOwnerId(UserId ownerId) {
        return dsl.selectFrom(Calendars.CALENDARS)
                .where(Calendars.CALENDARS.USER_ID.eq(ownerId.value()))
                .fetchOptional(PersistenceMapper::toCalendar);
    }
}
