package com.rosterguard.domain.event;

import java.time.Instant;

public class OutboxEvent {
    private String eventId;
    private String tenantId;
    private String aggregateId;
    private String eventType;
    private String payload;
    private String status; // PENDING, PUBLISHED, FAILED
    private int attempts;
    private Instant nextAttemptAt;
    private Instant createdAt;
    private Instant publishedAt;
    private String lastErrorCode;

    public OutboxEvent() {}

    public OutboxEvent(String eventId, String tenantId, String aggregateId,
                       String eventType, String payload, String status, int attempts,
                       Instant nextAttemptAt, Instant createdAt, Instant publishedAt, String lastErrorCode) {
        this.eventId = eventId;
        this.tenantId = tenantId;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
        this.lastErrorCode = lastErrorCode;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
}
