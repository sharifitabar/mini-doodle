package com.minidoodle.calendar.adapter.out.outbox;

import com.minidoodle.calendar.application.port.out.OutboxMessage;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;

/** The log format follows the delivery contract: envelope at INFO, payload at DEBUG. */
class OutboxLogContractTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID CALENDAR = UUID.randomUUID();
    private static final UUID SLOT = UUID.randomUUID();
    private static final UUID MEETING = UUID.randomUUID();
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-21T09:00:00Z");

    private final Logger logger =
            (Logger) LoggerFactory.getLogger(LoggingEventPublisher.LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    private OutboxMessage message() {
        String payload =
                "{\"meeting_id\":\""
                        + MEETING
                        + "\",\"title\":\"Planning\",\"participant_count\":2}";
        return new OutboxMessage(
                1L, EVENT_ID, "meeting.booked", 1, "slot", SLOT, CALENDAR, NOW, payload, 0);
    }

    private List<ILoggingEvent> eventsAt(Level level) {
        return appender.list.stream().filter(event -> event.getLevel() == level).toList();
    }

    @Test
    void writesOneInformationEntryWithTheEnvelope() {
        new LoggingEventPublisher().publish(message());

        List<ILoggingEvent> info = eventsAt(Level.INFO);
        assertThat(info).hasSize(1);
        String text = info.get(0).getFormattedMessage();
        assertThat(text)
                .contains("event=event.published")
                .contains("event_id=" + EVENT_ID)
                .contains("type=meeting.booked")
                .contains("schema_version=1")
                .contains("aggregate_type=slot")
                .contains("aggregate_id=" + SLOT)
                .contains("calendar_id=" + CALENDAR)
                .contains("occurred_at=");
    }

    @Test
    void keepsTheUserContentOutOfTheInformationEntry() {
        new LoggingEventPublisher().publish(message());

        assertThat(eventsAt(Level.INFO).get(0).getFormattedMessage()).doesNotContain("Planning");
        assertThat(eventsAt(Level.DEBUG)).hasSize(1);
        assertThat(eventsAt(Level.DEBUG).get(0).getFormattedMessage())
                .contains("event=event.payload")
                .contains("event_id=" + EVENT_ID)
                .contains("Planning");
    }
}
