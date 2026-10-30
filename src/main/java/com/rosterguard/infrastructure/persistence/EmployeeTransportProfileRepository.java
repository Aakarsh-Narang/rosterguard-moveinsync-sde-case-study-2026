package com.rosterguard.infrastructure.persistence;

import com.rosterguard.domain.employee.EmployeeTransportProfile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class EmployeeTransportProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public EmployeeTransportProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<EmployeeTransportProfile> rowMapper = (ResultSet rs, int rowNum) -> {
        EmployeeTransportProfile p = new EmployeeTransportProfile();
        p.setTenantId(rs.getString("tenant_id"));
        p.setEmployeeId(rs.getString("employee_id"));
        p.setPreferredPickupPoint(rs.getString("preferred_pickup_point"));
        p.setSpecialAssistanceNeeds(rs.getString("special_assistance_needs"));
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        p.setUpdatedAt(updatedTs != null ? updatedTs.toInstant() : null);
        return p;
    };

    public Optional<EmployeeTransportProfile> findById(String tenantId, String employeeId) {
        String sql = "SELECT * FROM employee_transport_profiles WHERE tenant_id = ? AND employee_id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, tenantId, employeeId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void upsert(EmployeeTransportProfile profile) {
        if (findById(profile.getTenantId(), profile.getEmployeeId()).isPresent()) {
            String updateSql = """
                UPDATE employee_transport_profiles 
                SET preferred_pickup_point = ?, special_assistance_needs = ?, updated_at = ?
                WHERE tenant_id = ? AND employee_id = ?
            """;
            jdbcTemplate.update(updateSql,
                    profile.getPreferredPickupPoint(),
                    profile.getSpecialAssistanceNeeds(),
                    Timestamp.from(Instant.now()),
                    profile.getTenantId(),
                    profile.getEmployeeId()
            );
        } else {
            String insertSql = """
                INSERT INTO employee_transport_profiles (tenant_id, employee_id, preferred_pickup_point, special_assistance_needs, updated_at)
                VALUES (?, ?, ?, ?, ?)
            """;
            jdbcTemplate.update(insertSql,
                    profile.getTenantId(),
                    profile.getEmployeeId(),
                    profile.getPreferredPickupPoint(),
                    profile.getSpecialAssistanceNeeds(),
                    Timestamp.from(Instant.now())
            );
        }
    }
}
