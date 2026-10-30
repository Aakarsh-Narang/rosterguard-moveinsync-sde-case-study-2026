package com.rosterguard.application.outbox;

import com.rosterguard.domain.event.OutboxEvent;

public interface EventPublisher {
    void publish(OutboxEvent event) throws Exception;
}
