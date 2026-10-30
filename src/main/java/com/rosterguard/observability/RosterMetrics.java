package com.rosterguard.observability;

import com.rosterguard.infrastructure.cache.ReferenceDataCache;
import com.rosterguard.infrastructure.persistence.OutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class RosterMetrics {

    private final MeterRegistry registry;

    // Event Ingestion Metrics
    private final Counter eventsReceivedCounter;
    private final Counter eventsAppliedCounter;
    private final Counter eventsDuplicateCounter;
    private final Counter eventsStaleCounter;
    private final Counter eventsRejectedCounter;
    private final Timer eventProcessingDurationTimer;

    // Outbox & Recovery Metrics
    private final Counter outboxRetryCounter;
    private final Timer outboxPublishDurationTimer;

    // Reconciliation Metrics
    private final Counter reconRowsProcessedCounter;
    private final Counter reconInvalidRowsCounter;
    private final Counter safetyBrakeTriggeredCounter;
    private final Timer reconDurationTimer;
    private final AtomicReference<Double> latestMissingRatio = new AtomicReference<>(0.0);

    // Downstream Side-Effect Metrics
    private final Counter bookingsCancelledCounter;
    private final Counter seatReleasesCounter;
    private final Counter consumerDuplicateCounter;

    public RosterMetrics(MeterRegistry registry,
                         OutboxRepository outboxRepository,
                         ReferenceDataCache referenceDataCache) {
        this.registry = registry;

        // Ingestion
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

        this.eventProcessingDurationTimer = Timer.builder("hr_event_processing_duration")
                .description("Execution latency for processing HR events")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);

        // Outbox
        this.outboxRetryCounter = Counter.builder("outbox_retry_total")
                .description("Total retry attempts for outbox event delivery")
                .register(registry);

        this.outboxPublishDurationTimer = Timer.builder("outbox_publish_duration")
                .description("Time taken to publish an outbox event downstream")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        Gauge.builder("outbox_pending_count", outboxRepository, OutboxRepository::countPendingEvents)
                .description("Current number of pending events waiting in outbox queue")
                .register(registry);

        Gauge.builder("outbox_oldest_pending_age_seconds", outboxRepository, OutboxRepository::getOldestPendingAgeSeconds)
                .description("Age in seconds of the oldest unconsumed event in outbox")
                .register(registry);

        // Reconciliation
        this.reconRowsProcessedCounter = Counter.builder("reconciliation_rows_processed_total")
                .description("Total nightly CSV reconciliation rows processed")
                .register(registry);

        this.reconInvalidRowsCounter = Counter.builder("reconciliation_invalid_rows_total")
                .description("Total malformed rows skipped during nightly reconciliation")
                .register(registry);

        this.safetyBrakeTriggeredCounter = Counter.builder("safety_brake_triggered_total")
                .description("Total times the safety brake halted mass deactivations")
                .register(registry);

        this.reconDurationTimer = Timer.builder("reconciliation_duration_seconds")
                .description("Duration of nightly reconciliation run")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        Gauge.builder("reconciliation_missing_ratio", latestMissingRatio, AtomicReference::get)
                .description("Missing employee ratio in the latest nightly reconciliation")
                .register(registry);

        // Downstream
        this.bookingsCancelledCounter = Counter.builder("cab_bookings_cancelled_total")
                .description("Total future cab bookings cancelled due to employee deactivations")
                .register(registry);

        this.seatReleasesCounter = Counter.builder("seat_releases_total")
                .description("Total vehicle seats released back into fleet")
                .register(registry);

        this.consumerDuplicateCounter = Counter.builder("consumer_duplicate_events_total")
                .description("Total duplicate downstream events deduplicated by consumer")
                .register(registry);

        // Caffeine Caching
        Gauge.builder("cache_hits_total", referenceDataCache, c -> c.getStats().hitCount())
                .description("Total reference data cache hits")
                .register(registry);

        Gauge.builder("cache_misses_total", referenceDataCache, c -> c.getStats().missCount())
                .description("Total reference data cache misses")
                .register(registry);

        Gauge.builder("cache_evictions_total", referenceDataCache, c -> c.getStats().evictionCount())
                .description("Total reference data cache evictions")
                .register(registry);

        Gauge.builder("cache_size", referenceDataCache, ReferenceDataCache::estimatedSize)
                .description("Current number of cached reference data entries")
                .register(registry);
    }

    public MeterRegistry getRegistry() { return registry; }

    public void incrementReceived() { eventsReceivedCounter.increment(); }
    public void incrementApplied() { eventsAppliedCounter.increment(); }
    public void incrementDuplicate() { eventsDuplicateCounter.increment(); }
    public void incrementStale() { eventsStaleCounter.increment(); }
    public void incrementRejected() { eventsRejectedCounter.increment(); }
    public Timer getEventProcessingTimer() { return eventProcessingDurationTimer; }

    public void incrementOutboxRetry() { outboxRetryCounter.increment(); }
    public Timer getOutboxPublishTimer() { return outboxPublishDurationTimer; }

    public void incrementReconRows(double count) { reconRowsProcessedCounter.increment(count); }
    public void incrementReconInvalid(double count) { reconInvalidRowsCounter.increment(count); }
    public void incrementSafetyBrake() { safetyBrakeTriggeredCounter.increment(); }
    public Timer getReconTimer() { return reconDurationTimer; }
    public void setLatestMissingRatio(double ratio) { latestMissingRatio.set(ratio); }

    public void incrementBookingsCancelled(double count) { bookingsCancelledCounter.increment(count); }
    public void incrementSeatReleases(double count) { seatReleasesCounter.increment(count); }
    public void incrementConsumerDuplicate() { consumerDuplicateCounter.increment(); }
}
