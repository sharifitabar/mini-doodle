package com.minidoodle.calendar.adapter.out.outbox;

import com.minidoodle.calendar.application.port.out.EventPublisher;
import com.minidoodle.calendar.application.port.out.OutboxMessage;
import com.minidoodle.calendar.application.port.out.OutboxMetrics;
import com.minidoodle.shared.config.OutboxProperties;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Delivers the pending rows in capture order. Each poll is one transaction with {@code SKIP
 * LOCKED}, so a second instance cannot take the same row.
 *
 * <p>The delivery target is a port. A failed delivery increments the attempt count and the row
 * stays pending. At the attempt limit the row becomes failed and the relay does not try it again.
 * The scheduled entry points only call {@link #pollOnce()} and {@link #cleanupOnce()}, so a test
 * drives the relay without a clock.
 */
@Component
class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingEventPublisher.LOGGER_NAME);

    private final OutboxProperties properties;
    private final JooqEventOutbox outbox;
    private final EventPublisher publisher;
    private final OutboxMetrics metrics;
    private final TransactionTemplate transactions;

    OutboxRelay(
            OutboxProperties properties,
            JooqEventOutbox outbox,
            EventPublisher publisher,
            OutboxMetrics metrics,
            PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.outbox = outbox;
        this.publisher = publisher;
        this.metrics = metrics;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** Runs one delivery cycle, then updates the pending count. */
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:500ms}")
    public void runPoll() {
        pollOnce();
        metrics.pending((int) outbox.countPending());
    }

    /** Delivers one batch. Returns the count of rows that this cycle read. */
    public int pollOnce() {
        Integer read =
                transactions.execute(
                        status -> {
                            List<OutboxMessage> batch = outbox.nextPending(properties.batchSize());
                            for (OutboxMessage message : batch) {
                                deliver(message);
                            }
                            return batch.size();
                        });
        return read == null ? 0 : read;
    }

    private void deliver(OutboxMessage message) {
        try {
            publisher.publish(message);
        } catch (RuntimeException ex) {
            outbox.markAttemptFailed(message.sequence(), ex.getMessage(), properties.maxAttempts());
            metrics.failed();
            LOG.warn(
                    "event=outbox.delivery_failed event_id={} attempt={} error={}",
                    message.eventId(),
                    message.attempts() + 1,
                    ex.getMessage());
            return;
        }
        outbox.markPublished(message.sequence());
        metrics.published();
    }

    /** Removes published rows older than the retention interval. */
    @Scheduled(fixedDelayString = "${app.outbox.cleanup-interval:1h}")
    public void runCleanup() {
        cleanupOnce();
    }

    public int cleanupOnce() {
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minus(properties.retention());
        int removed = outbox.deletePublishedBefore(threshold);
        if (removed > 0) {
            LOG.debug("event=outbox.cleanup removed={}", removed);
        }
        return removed;
    }
}
