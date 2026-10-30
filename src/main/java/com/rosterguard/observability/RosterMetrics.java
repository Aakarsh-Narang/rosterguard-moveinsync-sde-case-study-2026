package com.rosterguard.observability;

import com.rosterguard.infrastructure.persistence.OutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class RosterMetrics {

    private final Counter eventsReceivedCounter;
    private final Counter eventsAppliedCounter;
    private final Counter eventsDuplicateCounter;
    private final Counter eventsStaleCounter;
    private final Counter eventsRejectedCounter;
    private final Counter outboxRetryCounter;
    private final Counter reconRowsProcessedCounter;
    private final Counter reconInvalidRowsCounter;
    private final Counter safetyBrakeTriggeredCounter;
    private final Counter bookingsCancelledCounter;
    private final Timer reconDurationTimer;
    private final AtomicReference<Double> latestMissingRatio = new AtomicReference<>(0.0);

    public RosterMetrics(MeterRegistry registry, OutboxRepository outboxRepository) {
        this.eventsReceivedCounter = Counter.builder("hr_events_received_total")
                .description("Total HR events received across endpoints")
                .register(registry);

        this.eventsAppliedCounter = Counter.builder("hr_events_applied_total")
                .description("Total HR events successfully applied")
                .register(registry);

        this.eventsDuplicateCounter = Counter.builder("hr_events_duplicate_total")
                .description("Total duplicate HR events ignored")
                .register(registry);

        this.eventsStaleCounter = Counter.builder("hr_events_stale_total")
                .description("Total stale out-of-order HR events ignored")
                .register(registry);

        this.eventsRejectedCounter = Counter.builder("hr_events_rejected_total")
                .description("Total malformed or conflicting HR events rejected")
                .register(registry);

        this.outboxRetryCounter = Counter.builder("outbox_retry_total")
                .description("Total retry attempts for outbox event delivery")
                .register(registry);

        this.reconRowsProcessedCounter = Counter.builder("reconciliation_rows_processed_total")
                .description("Total nightly CSV reconciliation rows processed")
                .register(registry);

        this.reconInvalidRowsCounter = Counter.builder("reconciliation_invalid_rows_total")
                .description("Total malformed rows skipped during nightly reconciliation")
                .register(registry);

        this.safetyBrakeTriggeredCounter = Counter.builder("safety_brake_triggered_total")
                .description("Total times the safety brake halted mass deactivations")
                .register(registry);

        this.bookingsCancelledCounter = Counter.builder("cab_bookings_cancelled_total")
                .description("Total future cab bookings cancelled due to employee deactivations")
                .register(registry);

        this.reconDurationTimer = Timer.builder("reconciliation_duration_seconds")
                .description("Duration of nightly reconciliation run")
                .register(registry);

        Gauge.builder("outbox_pending_count", outboxRepository, OutboxRepository::countPendingEvents)
                .description("Current number of pending events waiting in outbox queue")
                .register(registry);

        Gauge.builder("reconciliation_missing_ratio", latestMissingRatio, AtomicReference::get)
                .description("Missing employee ratio in the latest nightly reconciliation")
                .register(registry);
    }

    public void incrementReceived() { eventsReceivedCounter.increment(); }
    public void incrementApplied() { eventsAppliedCounter.increment(); }
    public void incrementDuplicate() { eventsDuplicateCounter.increment(); }
    public void incrementStale() { eventsStaleCounter.increment(); }
    public void incrementRejected() { eventsRejectedCounter.increment(); }
    public void incrementOutboxRetry() { outboxRetryCounter.increment(); }
    public void incrementReconRows(double count) { reconRowsProcessedCounter.increment(count); }
    public void incrementReconInvalid(double count) { reconInvalidRowsCounter.increment(count); }
    public void incrementSafetyBrake() { safetyBrakeTriggeredCounter.increment(); }
    public void incrementBookingsCancelled(double count) { bookingsCancelledCounter.increment(count); }
    public Timer getReconTimer() { return reconDurationTimer; }
    public void setLatestMissingRatio(double ratio) { latestMissingRatio.set(ratio); }
}
