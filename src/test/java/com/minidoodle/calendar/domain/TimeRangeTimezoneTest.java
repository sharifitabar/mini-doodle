package com.minidoodle.calendar.domain;

import com.minidoodle.calendar.domain.model.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TimeRangeTimezoneTest {

    private static final ZoneId VIENNA = ZoneId.of("Europe/Vienna");

    @Test
    void durationIsElapsedTimeAcrossSpringForwardTransition() {
        // Europe/Vienna springs forward on 2026-03-29 at 02:00 local (+01:00 -> +02:00).
        // A 60-minute slot starting just before the gap must still last 60 real minutes.
        OffsetDateTime beforeGap = ZonedDateTime.of(2026, 3, 29, 1, 30, 0, 0, VIENNA)
                .toOffsetDateTime();

        TimeRange range = new TimeRange(beforeGap, 60);

        assertThat(range.start().toInstant()).isEqualTo(beforeGap.toInstant());
        assertThat(range.end().toInstant())
                .isEqualTo(beforeGap.toInstant().plus(60, ChronoUnit.MINUTES));
        assertThat(range.end()).isAfter(range.start());
    }

    @Test
    void durationIsElapsedTimeAcrossFallBackTransition() {
        // Europe/Vienna falls back on 2026-10-25 at 03:00 local (+02:00 -> +01:00).
        OffsetDateTime beforeFold = ZonedDateTime.of(2026, 10, 25, 1, 30, 0, 0, VIENNA)
                .toOffsetDateTime();

        TimeRange range = new TimeRange(beforeFold, 120);

        assertThat(range.end().toInstant())
                .isEqualTo(beforeFold.toInstant().plus(120, ChronoUnit.MINUTES));
    }

    @Test
    void sameInstantInDifferentOffsetsIsTheSameRange() {
        OffsetDateTime vienna = ZonedDateTime.of(2026, 6, 1, 10, 0, 0, 0, VIENNA)
                .toOffsetDateTime();
        OffsetDateTime utc = vienna.withOffsetSameInstant(ZoneOffset.UTC);

        assertThat(new TimeRange(vienna, 45)).isEqualTo(new TimeRange(utc, 45));
    }

    @Test
    void endUsesUtcWallClockAfterNormalization() {
        OffsetDateTime vienna = ZonedDateTime.of(2026, 6, 1, 10, 0, 0, 0, VIENNA)
                .toOffsetDateTime();

        TimeRange range = new TimeRange(vienna, 90);

        assertThat(range.start().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(range.end()).isEqualTo(range.start().plusMinutes(90));
    }
}
