package com.minidoodle.calendar.adapter.out.outbox;

import com.minidoodle.calendar.application.port.out.EventPublisher;
import com.minidoodle.calendar.application.port.out.OutboxMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Writes one information-level entry for each delivered event. The entry has the envelope only, so
 * user content stays out of the default log. The payload is available at the debug level.
 */
@Component
class LoggingEventPublisher implements EventPublisher {

    static final String LOGGER_NAME = "com.minidoodle.outbox";

    private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);

    @Override
    public void publish(OutboxMessage message) {
        LOG.info(
                "event=event.published event_id={} type={} schema_version={} aggregate_type={} "
                        + "aggregate_id={} calendar_id={} occurred_at={}",
                message.eventId(),
                message.eventType(),
                message.schemaVersion(),
                message.aggregateType(),
                message.aggregateId(),
                message.calendarId(),
                message.occurredAt());
        LOG.debug(
                "event=event.payload event_id={} payload={}",
                message.eventId(),
                message.payloadJson());
    }
}
