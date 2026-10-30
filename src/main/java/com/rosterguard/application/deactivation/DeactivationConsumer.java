package com.rosterguard.application.deactivation;

import com.rosterguard.domain.event.OutboxEvent;
import com.rosterguard.infrastructure.persistence.CabBookingRepository;
import com.rosterguard.infrastructure.persistence.ConsumedEventsRepository;
import com.rosterguard.observability.RosterMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class DeactivationConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeactivationConsumer.class);
    private static final String CONSUMER_NAME = "deactivation-side-effects-consumer";

    private final CabBookingRepository cabBookingRepository;
    private final ConsumedEventsRepository consumedEventsRepository;
    private final RosterMetrics rosterMetrics;

    public DeactivationConsumer(CabBookingRepository cabBookingRepository,
                                ConsumedEventsRepository consumedEventsRepository,
                                RosterMetrics rosterMetrics) {
        this.cabBookingRepository = cabBookingRepository;
        this.consumedEventsRepository = consumedEventsRepository;
        this.rosterMetrics = rosterMetrics;
    }

    @Transactional
    public void processDeactivationEvent(OutboxEvent event) {
        if (!"EmployeeDeactivated".equals(event.getEventType())) {
            return;
        }

        // 1. Idempotency check via consumed_events deduplication table
        if (consumedEventsRepository.isConsumed(CONSUMER_NAME, event.getEventId())) {
            rosterMetrics.incrementConsumerDuplicate();
            log.info("Event {} already processed by {}, skipping duplicate execution",
                    event.getEventId(), CONSUMER_NAME);
            return;
        }

        String tenantId = event.getTenantId();
        String employeeId = event.getAggregateId();
        Instant now = Instant.now();

        // 2. Cancel future cab bookings (pickup_time > now) and release seats
        int cancelledCount = cabBookingRepository.cancelFutureBookingsAndFreeSeats(tenantId, employeeId, now);
        if (cancelledCount > 0) {
            rosterMetrics.incrementBookingsCancelled(cancelledCount);
            rosterMetrics.incrementSeatReleases(cancelledCount);
            log.info("Cancelled {} future cab bookings and released seats for employee {}", cancelledCount, employeeId);
        }

        // 3. Record event consumption atomically
        consumedEventsRepository.markConsumed(CONSUMER_NAME, event.getEventId());
    }
}
