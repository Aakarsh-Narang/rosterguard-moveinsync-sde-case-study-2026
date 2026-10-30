package com.rosterguard.infrastructure.persistence;

import com.rosterguard.domain.event.ProcessingOutcome;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class IdempotencyRepository {

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record IdempotencyReceipt(String tenantId, String sourceSystem, String employeeId,
                                     long sourceVersion, String payloadHash, ProcessingOutcome outcome,
                                     Instant processedAt) {}

    public Optional<IdempotencyReceipt> findReceipt(String tenantId, String sourceSystem,
                                                    String employeeId, long sourceVersion) {
        String sql = """
            SELECT tenant_id, source_system, employee_id, source_version, payload_hash, processing_outcome, processed_at
            FROM idempotency_receipts
            WHERE tenant_id = ? AND source_system = ? AND employee_id = ? AND source_version = ?
        """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new IdempotencyReceipt(
                    rs.getString("tenant_id"),
                    rs.getString("source_system"),
                    rs.getString("employee_id"),
                    rs.getLong("source_version"),
                    rs.getString("payload_hash"),
                    ProcessingOutcome.valueOf(rs.getString("processing_outcome")),
                    rs.getTimestamp("processed_at").toInstant()
            ), tenantId, sourceSystem, employeeId, sourceVersion));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean saveReceipt(String tenantId, String sourceSystem, String employeeId,
                               long sourceVersion, String payloadHash, ProcessingOutcome outcome) {
        String sql = """
            INSERT INTO idempotency_receipts 
            (tenant_id, source_system, employee_id, source_version, payload_hash, processing_outcome, processed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try {
            jdbcTemplate.update(sql,
                    tenantId,
                    sourceSystem,
                    employeeId,
                    sourceVersion,
                    payloadHash,
                    outcome.name(),
                    Timestamp.from(Instant.now())
            );
            return true;
        } catch (DuplicateKeyException e) {
            // Receipt already exists (concurrent identical insert)
            return false;
        }
    }
}
