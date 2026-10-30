package com.rosterguard.infrastructure.persistence;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record UserRecord(long id, String tenantId, String username, String passwordHash, String role, boolean active) {}

    public Optional<UserRecord> findByTenantAndUsername(String tenantId, String username) {
        String sql = "SELECT id, tenant_id, username, password_hash, role, active FROM users WHERE tenant_id = ? AND username = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new UserRecord(
                    rs.getLong("id"),
                    rs.getString("tenant_id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    rs.getString("role"),
                    rs.getBoolean("active")
            ), tenantId, username));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void insertTenantAndUser(String tenantId, String tenantName, String username, String passwordHash, String role) {
        Integer tenantCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tenants WHERE id = ?", Integer.class, tenantId);
        if (tenantCount == null || tenantCount == 0) {
            String tenantSql = "INSERT INTO tenants (id, name, status, created_at) VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP)";
            jdbcTemplate.update(tenantSql, tenantId, tenantName);
        }

        Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE tenant_id = ? AND username = ?", Integer.class, tenantId, username);
        if (userCount == null || userCount == 0) {
            String userSql = "INSERT INTO users (tenant_id, username, password_hash, role, active) VALUES (?, ?, ?, ?, TRUE)";
            jdbcTemplate.update(userSql, tenantId, username, passwordHash, role);
        }
    }
}
