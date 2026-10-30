package com.rosterguard.application.outbox;

import com.rosterguard.domain.event.OutboxEvent;
import com.rosterguard.infrastructure.persistence.OutboxRepository;
import com.rosterguard.observability.RosterMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class OutboxPollingWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxPollingWorker.class);

    private final OutboxRepository outboxRepository;
    private final EventPublisher eventPublisher;
    private final RosterMetrics rosterMetrics;
    private final int batchSize;
    private final int maxRetries;

    public OutboxPollingWorker(OutboxRepository outboxRepository,
                               EventPublisher eventPublisher,
                               RosterMetrics rosterMetrics,
                               @Value("${roster.outbox.batch-size:50}") int batchSize,
                               @Value("${roster.outbox.max-retries:5}") int maxRetries) {
        this.outboxRepository = outboxRepository;
        this.eventPublisher = eventPublisher;
        this.rosterMetrics = rosterMetrics;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
    }

    @Scheduled(fixedDelayString = "${roster.outbox.polling-interval-ms:1000}")
    public void pollAndPublish() {
        processPendingOutboxBatch();
    }

    public int processPendingOutboxBatch() {
        List<OutboxEvent> claimedEvents = outboxRepository.claimPendingEvents(batchSize);
        if (claimedEvents.isEmpty()) {
            return 0;
        }

        int processed = 0;
        for (OutboxEvent event : claimedEvents) {
            try {
                // Publish without holding long database lock
                eventPublisher.publish(event);
                outboxRepository.markPublished(event.getEventId());
                processed++;
            } catch (Exception e) {
                rosterMetrics.incrementOutboxRetry();
                int nextAttempts = event.getAttempts() + 1;
                if (nextAttempts >= maxRetries) {
                    log.error("Outbox event {} exceeded max retries ({}), marking failed",
                            event.getEventId(), maxRetries);
                    outboxRepository.markFailed(event.getEventId(), "MAX_RETRIES_EXCEEDED",
                            Instant.now().plus(Duration.ofHours(24)), nextAttempts);
                } else {
                    // Exponential backoff: 2^attempts seconds
                    long backoffSec = (long) Math.pow(2, nextAttempts);
                    Instant nextAttemptAt = Instant.now().plus(Duration.ofSeconds(backoffSec));
                    log.warn("Failed to publish outbox event {}, scheduling retry in {}s",
                            event.getEventId(), backoffSec);
                    outboxRepository.markFailed(event.getEventId(), e.getClass().getSimpleName(),
                            nextAttemptAt, nextAttempts);
                }
            }
        }
        return processed;
    }
}
