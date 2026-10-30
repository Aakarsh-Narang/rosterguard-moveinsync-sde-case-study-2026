package com.rosterguard.infrastructure.persistence;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class OfficeRepository {

    private final JdbcTemplate jdbcTemplate;

    public OfficeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean exists(String tenantId, String officeCode) {
        String sql = "SELECT COUNT(*) FROM offices WHERE tenant_id = ? AND office_code = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tenantId, officeCode);
        return count != null && count > 0;
    }

    public void insert(String tenantId, String officeCode, String officeName) {
        if (!exists(tenantId, officeCode)) {
            String sql = "INSERT INTO offices (tenant_id, office_code, office_name) VALUES (?, ?, ?)";
            jdbcTemplate.update(sql, tenantId, officeCode, officeName);
        }
    }
}
