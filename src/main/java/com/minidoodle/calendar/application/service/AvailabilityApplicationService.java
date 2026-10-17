package com.minidoodle.calendar.application.service;

import com.minidoodle.calendar.application.port.in.AvailabilityQuery;
import com.minidoodle.calendar.application.port.in.GetAvailabilityUseCase;
import com.minidoodle.calendar.application.port.out.AvailabilityRepository;
import com.minidoodle.calendar.domain.model.Availability;
import com.minidoodle.calendar.domain.model.Calendar;
import com.minidoodle.calendar.domain.model.SlotOccupancy;
import com.minidoodle.calendar.domain.model.TimeFrame;
import com.minidoodle.calendar.domain.model.UserId;
import com.minidoodle.calendar.domain.service.AvailabilityCalculator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class AvailabilityApplicationService implements GetAvailabilityUseCase {

    private final CallerCalendarResolver calendarResolver;
    private final AvailabilityRepository occupancies;
    private final AvailabilityMetrics metrics;
    private final AvailabilityCalculator calculator = new AvailabilityCalculator();

    AvailabilityApplicationService(
            CallerCalendarResolver calendarResolver,
            AvailabilityRepository occupancies,
            AvailabilityMetrics metrics) {
        this.calendarResolver = calendarResolver;
        this.occupancies = occupancies;
        this.metrics = metrics;
    }

    @Override
    public Availability getAvailability(UserId caller, AvailabilityQuery query) {
        return metrics.timeQuery(() -> {
            TimeFrame frame = new TimeFrame(query.from(), query.to());
            Calendar calendar = calendarResolver.resolve(caller);
            List<SlotOccupancy> found = occupancies.findOccupancies(calendar.id(), frame);
            Availability availability = calculator.calculate(frame, found);
            metrics.recordQuery(availability);
            return availability;
        });
    }
}
