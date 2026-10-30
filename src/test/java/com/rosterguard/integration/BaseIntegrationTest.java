package com.rosterguard.integration;

import com.rosterguard.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void initTenant() {
        TenantContext.setContext("tenant-moveinsync", "test-user", "ADMIN");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    protected void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM consumed_events");
        jdbcTemplate.execute("DELETE FROM cab_bookings");
        jdbcTemplate.execute("DELETE FROM outbox_events");
        jdbcTemplate.execute("DELETE FROM idempotency_receipts");
        jdbcTemplate.execute("DELETE FROM employee_transport_profiles");
        jdbcTemplate.execute("DELETE FROM reconciliation_staging");
        jdbcTemplate.execute("DELETE FROM reconciliation_runs");
        jdbcTemplate.execute("DELETE FROM employees");
    }
}
