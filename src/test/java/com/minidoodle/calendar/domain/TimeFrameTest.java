package com.minidoodle.calendar.domain;

import com.minidoodle.calendar.domain.exception.InvalidTimeFrameException;
import com.minidoodle.calendar.domain.model.TimeFrame;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeFrameTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-11-02T00:00:00Z");

    @Test
    void normalizesBoundsToUtc() {
        TimeFrame frame = new TimeFrame(
                OffsetDateTime.parse("2026-11-02T02:00:00+02:00"),
                OffsetDateTime.parse("2026-11-02T03:00:00+02:00"));

        assertThat(frame.start()).isEqualTo(OffsetDateTime.parse("2026-11-02T00:00:00Z"));
        assertThat(frame.end()).isEqualTo(OffsetDateTime.parse("2026-11-02T01:00:00Z"));
        assertThat(frame.start().getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void truncatesToMicroseconds() {
        OffsetDateTime start = OffsetDateTime.parse("2026-11-02T00:00:00.123456789Z");
        TimeFrame frame = new TimeFrame(start, start.plusHours(1));

        assertThat(frame.start()).isEqualTo(OffsetDateTime.parse("2026-11-02T00:00:00.123456Z"));
    }

    @Test
    void rejectsMissingBounds() {
        assertThatThrownBy(() -> new TimeFrame(null, START))
                .isInstanceOf(InvalidTimeFrameException.class);
        assertThatThrownBy(() -> new TimeFrame(START, null))
                .isInstanceOf(InvalidTimeFrameException.class);
    }

    @Test
    void rejectsInvertedOrEmptyFrame() {
        assertThatThrownBy(() -> new TimeFrame(START, START))
                .isInstanceOf(InvalidTimeFrameException.class);
        assertThatThrownBy(() -> new TimeFrame(START, START.minusHours(1)))
                .isInstanceOf(InvalidTimeFrameException.class);
    }

    @Test
    void acceptsFrameUpToMaxSpanAndRejectsBeyondIt() {
        TimeFrame max = new TimeFrame(START, START.plusDays(31));
        assertThat(max.end()).isEqualTo(START.plusDays(31));

        assertThatThrownBy(() -> new TimeFrame(START, START.plusDays(31).plusMinutes(1)))
                .isInstanceOf(InvalidTimeFrameException.class);
    }
}
