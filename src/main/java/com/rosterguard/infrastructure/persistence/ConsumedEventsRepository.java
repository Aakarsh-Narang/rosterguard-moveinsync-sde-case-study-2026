package com.rosterguard.infrastructure.persistence;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class ConsumedEventsRepository {

    private final JdbcTemplate jdbcTemplate;

    public ConsumedEventsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isConsumed(String consumerName, String eventId) {
        String sql = "SELECT COUNT(*) FROM consumed_events WHERE consumer_name = ? AND event_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, consumerName, eventId);
        return count != null && count > 0;
    }

    public boolean markConsumed(String consumerName, String eventId) {
        String sql = "INSERT INTO consumed_events (consumer_name, event_id, processed_at) VALUES (?, ?, ?)";
        try {
            jdbcTemplate.update(sql, consumerName, eventId, Timestamp.from(Instant.now()));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}
