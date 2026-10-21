package com.minidoodle.calendar.application.port.out;

/** Sends one stored event to the delivery target. The first adapter writes the application log. */
public interface EventPublisher {

    void publish(OutboxMessage message);
}
