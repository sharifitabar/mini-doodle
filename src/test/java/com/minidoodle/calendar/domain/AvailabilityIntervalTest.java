package com.minidoodle.calendar.domain;

import com.minidoodle.calendar.domain.exception.InvalidTimeFrameException;
import com.minidoodle.calendar.domain.model.AvailabilityInterval;
import com.minidoodle.calendar.domain.model.AvailabilityKind;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvailabilityIntervalTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-11-02T09:00:00Z");

    @Test
    void buildsAndNormalizesBounds() {
        AvailabilityInterval interval = new AvailabilityInterval(
                OffsetDateTime.parse("2026-11-02T11:00:00+02:00"),
                OffsetDateTime.parse("2026-11-02T12:00:00+02:00"),
                AvailabilityKind.FREE);

        assertThat(interval.start()).isEqualTo(START);
        assertThat(interval.end()).isEqualTo(START.plusHours(1));
        assertThat(interval.start().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(interval.kind()).isEqualTo(AvailabilityKind.FREE);
    }

    @Test
    void rejectsEndNotAfterStart() {
        assertThatThrownBy(() -> new AvailabilityInterval(START, START, AvailabilityKind.FREE))
                .isInstanceOf(InvalidTimeFrameException.class);
        assertThatThrownBy(() -> new AvailabilityInterval(START, START.minusMinutes(1), AvailabilityKind.FREE))
                .isInstanceOf(InvalidTimeFrameException.class);
    }

    @Test
    void rejectsMissingValues() {
        assertThatThrownBy(() -> new AvailabilityInterval(null, START, AvailabilityKind.FREE))
                .isInstanceOf(InvalidTimeFrameException.class);
        assertThatThrownBy(() -> new AvailabilityInterval(START, null, AvailabilityKind.FREE))
                .isInstanceOf(InvalidTimeFrameException.class);
        assertThatThrownBy(() -> new AvailabilityInterval(START, START.plusHours(1), null))
                .isInstanceOf(InvalidTimeFrameException.class);
    }
}
