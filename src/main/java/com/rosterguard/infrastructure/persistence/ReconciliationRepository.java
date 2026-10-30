package com.rosterguard.infrastructure.persistence;

import com.rosterguard.domain.reconciliation.ReconciliationStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ReconciliationRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReconciliationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record ReconciliationRun(
            String runId, String tenantId, Instant snapshotGeneratedAt,
            int totalRows, int validRows, int invalidRows,
            int missingEmployees, double missingRatio,
            ReconciliationStatus status, String operatorApproval,
            Instant startedAt, Instant completedAt
    ) {}

    public record StagingRecord(
            String employeeId, long sourceVersion, String canonicalHash,
            String name, String email, String phone,
            String managerId, String officeCode, boolean active
    ) {}

    public record DiffSummary(
            int newCount, int changedCount, int unchangedCount, int missingCount
    ) {}

    public void createRun(String runId, String tenantId, Instant snapshotGeneratedAt) {
        String sql = """
            INSERT INTO reconciliation_runs 
            (run_id, tenant_id, snapshot_generated_at, total_rows, valid_rows, invalid_rows, missing_employees, missing_ratio, status, started_at)
            VALUES (?, ?, ?, 0, 0, 0, 0, 0.0, 'PENDING', ?)
        """;
        jdbcTemplate.update(sql, runId, tenantId, Timestamp.from(snapshotGeneratedAt), Timestamp.from(Instant.now()));
    }

    public void updateRunProgress(String runId, int totalRows, int validRows, int invalidRows,
                                  int missingEmployees, double missingRatio, ReconciliationStatus status,
                                  String operatorApproval, Instant completedAt) {
        String sql = """
            UPDATE reconciliation_runs 
            SET total_rows = ?, valid_rows = ?, invalid_rows = ?, missing_employees = ?, missing_ratio = ?, status = ?, operator_approval = ?, completed_at = ?
            WHERE run_id = ?
        """;
        jdbcTemplate.update(sql, totalRows, validRows, invalidRows, missingEmployees, missingRatio,
                status.name(), operatorApproval, completedAt != null ? Timestamp.from(completedAt) : null, runId);
    }

    public Optional<ReconciliationRun> findRunById(String tenantId, String runId) {
        String sql = "SELECT * FROM reconciliation_runs WHERE tenant_id = ? AND run_id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new ReconciliationRun(
                    rs.getString("run_id"),
                    rs.getString("tenant_id"),
                    rs.getTimestamp("snapshot_generated_at").toInstant(),
                    rs.getInt("total_rows"),
                    rs.getInt("valid_rows"),
                    rs.getInt("invalid_rows"),
                    rs.getInt("missing_employees"),
                    rs.getDouble("missing_ratio"),
                    ReconciliationStatus.valueOf(rs.getString("status")),
                    rs.getString("operator_approval"),
                    rs.getTimestamp("started_at").toInstant(),
                    rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null
            ), tenantId, runId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void batchInsertStaging(String runId, String tenantId, List<StagingRecord> records) {
        String sql = """
            INSERT INTO reconciliation_staging 
            (run_id, tenant_id, employee_id, source_version, canonical_hash, name, email, phone, manager_id, office_code, active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        jdbcTemplate.batchUpdate(sql, records, records.size(), (PreparedStatement ps, StagingRecord r) -> {
            ps.setString(1, runId);
            ps.setString(2, tenantId);
            ps.setString(3, r.employeeId());
            ps.setLong(4, r.sourceVersion());
            ps.setString(5, r.canonicalHash());
            ps.setString(6, r.name());
            ps.setString(7, r.email());
            ps.setString(8, r.phone());
            ps.setString(9, r.managerId());
            ps.setString(10, r.officeCode());
            ps.setBoolean(11, r.active());
        });
    }

    public int countMissingActiveEmployees(String runId, String tenantId) {
        // Active employees in store who are NOT in the staging snapshot for this run
        String sql = """
            SELECT COUNT(*) 
            FROM employees e
            LEFT JOIN reconciliation_staging s 
                ON e.tenant_id = s.tenant_id 
                AND e.employee_id = s.employee_id 
                AND s.run_id = ?
            WHERE e.tenant_id = ? 
              AND e.active = TRUE 
              AND s.employee_id IS NULL
        """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, runId, tenantId);
        return count != null ? count : 0;
    }

    public List<String> findMissingActiveEmployeeIds(String runId, String tenantId) {
        String sql = """
            SELECT e.employee_id 
            FROM employees e
            LEFT JOIN reconciliation_staging s 
                ON e.tenant_id = s.tenant_id 
                AND e.employee_id = s.employee_id 
                AND s.run_id = ?
            WHERE e.tenant_id = ? 
              AND e.active = TRUE 
              AND s.employee_id IS NULL
        """;
        return jdbcTemplate.queryForList(sql, String.class, runId, tenantId);
    }

    public int applyNewAndChangedFromStaging(String runId, String tenantId, Instant snapshotTime) {
        // 1. Insert new employees from staging
        String insertNewSql = """
            INSERT INTO employees 
            (tenant_id, employee_id, name, email, phone, manager_id, office_code, source_system, source_version, payload_hash, active, last_hr_update_at, updated_at)
            SELECT s.tenant_id, s.employee_id, s.name, s.email, s.phone, s.manager_id, s.office_code, 'NIGHTLY_RECON', s.source_version, s.canonical_hash, s.active, ?, ?
            FROM reconciliation_staging s
            LEFT JOIN employees e 
                ON s.tenant_id = e.tenant_id AND s.employee_id = e.employee_id
            WHERE s.run_id = ? AND s.tenant_id = ? AND e.employee_id IS NULL
        """;
        int newInserted = jdbcTemplate.update(insertNewSql, Timestamp.from(snapshotTime), Timestamp.from(Instant.now()), runId, tenantId);

        // 2. Update existing employees ONLY if snapshot version >= stored version AND payload hash changed
        String updateChangedSql = """
            UPDATE employees e
            SET name = s.name,
                email = s.email,
                phone = s.phone,
                manager_id = s.manager_id,
                office_code = s.office_code,
                source_version = s.source_version,
                payload_hash = s.canonical_hash,
                active = s.active,
                last_hr_update_at = ?,
                updated_at = ?
            FROM reconciliation_staging s
            WHERE e.tenant_id = s.tenant_id 
              AND e.employee_id = s.employee_id
              AND s.run_id = ? 
              AND s.tenant_id = ?
              AND s.source_version >= e.source_version
              AND s.canonical_hash <> e.payload_hash
        """;
        int changedUpdated = jdbcTemplate.update(updateChangedSql, Timestamp.from(snapshotTime), Timestamp.from(Instant.now()), runId, tenantId);

        return newInserted + changedUpdated;
    }

    public int deactivateMissingEmployees(String runId, String tenantId, Instant snapshotTime) {
        String sql = """
            UPDATE employees e
            SET active = FALSE,
                updated_at = ?
            FROM (
                SELECT e2.employee_id
                FROM employees e2
                LEFT JOIN reconciliation_staging s 
                    ON e2.tenant_id = s.tenant_id 
                    AND e2.employee_id = s.employee_id 
                    AND s.run_id = ?
                WHERE e2.tenant_id = ? 
                  AND e2.active = TRUE 
                  AND s.employee_id IS NULL
                  AND e2.last_hr_update_at <= ?
            ) missing
            WHERE e.tenant_id = ? AND e.employee_id = missing.employee_id
        """;
        return jdbcTemplate.update(sql, Timestamp.from(Instant.now()), runId, tenantId, Timestamp.from(snapshotTime), tenantId);
    }

    public void cleanStaging(String runId) {
        jdbcTemplate.update("DELETE FROM reconciliation_staging WHERE run_id = ?", runId);
    }
}
