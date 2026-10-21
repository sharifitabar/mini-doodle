package com.minidoodle.calendar.application.port.out;

/** The outbox meters: delivered events, failed events, and events that wait for delivery. */
public interface OutboxMetrics {

    void published();

    void failed();

    void pending(int count);
}
