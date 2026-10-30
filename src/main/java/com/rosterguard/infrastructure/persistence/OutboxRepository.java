package com.rosterguard.infrastructure.persistence;

import com.rosterguard.domain.event.OutboxEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public OutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<OutboxEvent> rowMapper = (ResultSet rs, int rowNum) -> {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(rs.getString("event_id"));
        event.setTenantId(rs.getString("tenant_id"));
        event.setAggregateId(rs.getString("aggregate_id"));
        event.setEventType(rs.getString("event_type"));
        event.setPayload(rs.getString("payload"));
        event.setStatus(rs.getString("status"));
        event.setAttempts(rs.getInt("attempts"));
        
        Timestamp nextAttempt = rs.getTimestamp("next_attempt_at");
        event.setNextAttemptAt(nextAttempt != null ? nextAttempt.toInstant() : null);
        
        Timestamp created = rs.getTimestamp("created_at");
        event.setCreatedAt(created != null ? created.toInstant() : null);
        
        Timestamp published = rs.getTimestamp("published_at");
        event.setPublishedAt(published != null ? published.toInstant() : null);
        
        event.setLastErrorCode(rs.getString("last_error_code"));
        return event;
    };

    public void insert(OutboxEvent event) {
        String sql = """
            INSERT INTO outbox_events 
            (event_id, tenant_id, aggregate_id, event_type, payload, status, attempts, next_attempt_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        jdbcTemplate.update(sql,
                event.getEventId(),
                event.getTenantId(),
                event.getAggregateId(),
                event.getEventType(),
                event.getPayload(),
                event.getStatus() != null ? event.getStatus() : "PENDING",
                event.getAttempts(),
                Timestamp.from(event.getNextAttemptAt() != null ? event.getNextAttemptAt() : Instant.now()),
                Timestamp.from(event.getCreatedAt() != null ? event.getCreatedAt() : Instant.now())
        );
    }

    public List<OutboxEvent> claimPendingEvents(int limit) {
        // FOR UPDATE SKIP LOCKED prevents concurrent workers from contending on identical rows
        String sql = """
            SELECT * FROM outbox_events 
            WHERE status = 'PENDING' AND next_attempt_at <= ?
            ORDER BY created_at ASC
            LIMIT ?
            FOR UPDATE SKIP LOCKED
        """;
        return jdbcTemplate.query(sql, rowMapper, Timestamp.from(Instant.now()), limit);
    }

    public void markPublished(String eventId) {
        String sql = """
            UPDATE outbox_events 
            SET status = 'PUBLISHED', published_at = ?
            WHERE event_id = ?
        """;
        jdbcTemplate.update(sql, Timestamp.from(Instant.now()), eventId);
    }

    public void markFailed(String eventId, String errorCode, Instant nextAttemptAt, int attempts) {
        String sql = """
            UPDATE outbox_events 
            SET attempts = ?, next_attempt_at = ?, last_error_code = ?
            WHERE event_id = ?
        """;
        jdbcTemplate.update(sql, attempts, Timestamp.from(nextAttemptAt), errorCode, eventId);
    }

    public int countPendingEvents() {
        String sql = "SELECT COUNT(*) FROM outbox_events WHERE status = 'PENDING'";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    public double getOldestPendingAgeSeconds() {
        String sql = "SELECT EXTRACT(EPOCH FROM (NOW() - MIN(created_at))) FROM outbox_events WHERE status = 'PENDING'";
        try {
            Double age = jdbcTemplate.queryForObject(sql, Double.class);
            return age != null && age > 0 ? age : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
