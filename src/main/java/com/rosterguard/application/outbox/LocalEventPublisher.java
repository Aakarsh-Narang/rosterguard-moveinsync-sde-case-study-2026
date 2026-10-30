package com.rosterguard.application.outbox;

import com.rosterguard.application.deactivation.DeactivationConsumer;
import com.rosterguard.domain.event.OutboxEvent;
import org.springframework.stereotype.Component;

@Component
public class LocalEventPublisher implements EventPublisher {

    private final DeactivationConsumer deactivationConsumer;

    public LocalEventPublisher(DeactivationConsumer deactivationConsumer) {
        this.deactivationConsumer = deactivationConsumer;
    }

    @Override
    public void publish(OutboxEvent event) throws Exception {
        if ("EmployeeDeactivated".equals(event.getEventType())) {
            deactivationConsumer.processDeactivationEvent(event);
        }
        // Additional domain event types can be dispatched here or bridged to Kafka/Redpanda
    }
}
