package com.rosterguard.infrastructure.persistence;

import com.rosterguard.domain.employee.Employee;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class EmployeeRepository {

    private final JdbcTemplate jdbcTemplate;

    public EmployeeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Employee> rowMapper = (ResultSet rs, int rowNum) -> {
        Employee e = new Employee();
        e.setTenantId(rs.getString("tenant_id"));
        e.setEmployeeId(rs.getString("employee_id"));
        e.setName(rs.getString("name"));
        e.setEmail(rs.getString("email"));
        e.setPhone(rs.getString("phone"));
        e.setManagerId(rs.getString("manager_id"));
        e.setOfficeCode(rs.getString("office_code"));
        e.setSourceSystem(rs.getString("source_system"));
        e.setSourceVersion(rs.getLong("source_version"));
        e.setPayloadHash(rs.getString("payload_hash"));
        e.setActive(rs.getBoolean("active"));
        
        Timestamp lastHrTs = rs.getTimestamp("last_hr_update_at");
        e.setLastHrUpdateAt(lastHrTs != null ? lastHrTs.toInstant() : null);
        
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        e.setUpdatedAt(updatedTs != null ? updatedTs.toInstant() : null);
        return e;
    };

    public Optional<Employee> findById(String tenantId, String employeeId) {
        String sql = "SELECT * FROM employees WHERE tenant_id = ? AND employee_id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, tenantId, employeeId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Employee> findForUpdate(String tenantId, String employeeId) {
        String sql = "SELECT * FROM employees WHERE tenant_id = ? AND employee_id = ? FOR UPDATE";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, tenantId, employeeId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void insert(Employee e) {
        String sql = """
            INSERT INTO employees 
            (tenant_id, employee_id, name, email, phone, manager_id, office_code, source_system, source_version, payload_hash, active, last_hr_update_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        jdbcTemplate.update(sql,
                e.getTenantId(),
                e.getEmployeeId(),
                e.getName(),
                e.getEmail(),
                e.getPhone(),
                e.getManagerId(),
                e.getOfficeCode(),
                e.getSourceSystem(),
                e.getSourceVersion(),
                e.getPayloadHash(),
                e.isActive(),
                Timestamp.from(e.getLastHrUpdateAt() != null ? e.getLastHrUpdateAt() : Instant.now()),
                Timestamp.from(Instant.now())
        );
    }

    public boolean update(Employee e) {
        String sql = """
            UPDATE employees 
            SET name = ?, email = ?, phone = ?, manager_id = ?, office_code = ?, source_system = ?, source_version = ?, payload_hash = ?, active = ?, last_hr_update_at = ?, updated_at = ?
            WHERE tenant_id = ? AND employee_id = ? AND source_version < ?
        """;
        int rows = jdbcTemplate.update(sql,
                e.getName(),
                e.getEmail(),
                e.getPhone(),
                e.getManagerId(),
                e.getOfficeCode(),
                e.getSourceSystem(),
                e.getSourceVersion(),
                e.getPayloadHash(),
                e.isActive(),
                Timestamp.from(e.getLastHrUpdateAt() != null ? e.getLastHrUpdateAt() : Instant.now()),
                Timestamp.from(Instant.now()),
                e.getTenantId(),
                e.getEmployeeId(),
                e.getSourceVersion()
        );
        return rows > 0;
    }

    public int countActiveEmployees(String tenantId) {
        String sql = "SELECT COUNT(*) FROM employees WHERE tenant_id = ? AND active = TRUE";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tenantId);
        return count != null ? count : 0;
    }

    public List<String> findActiveEmployeeIds(String tenantId) {
        String sql = "SELECT employee_id FROM employees WHERE tenant_id = ? AND active = TRUE";
        return jdbcTemplate.queryForList(sql, String.class, tenantId);
    }

    public void deactivate(String tenantId, String employeeId, long sourceVersion, Instant updateTime) {
        String sql = """
            UPDATE employees 
            SET active = FALSE, source_version = ?, last_hr_update_at = ?, updated_at = ?
            WHERE tenant_id = ? AND employee_id = ? AND source_version <= ?
        """;
        jdbcTemplate.update(sql,
                sourceVersion,
                Timestamp.from(updateTime),
                Timestamp.from(Instant.now()),
                tenantId,
                employeeId,
                sourceVersion
        );
    }
}
