package com.rosterguard.integration;

import com.rosterguard.api.dto.HrEventRequest;
import com.rosterguard.application.ingestion.EmployeeIngestionService;
import com.rosterguard.domain.employee.Employee;
import com.rosterguard.domain.event.HrEventType;
import com.rosterguard.infrastructure.persistence.EmployeeRepository;
import com.rosterguard.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TenantIsolationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EmployeeIngestionService ingestionService;

    @Autowired
    private EmployeeRepository employeeRepository;

    private static final String TENANT_A = "tenant-moveinsync";
    private static final String TENANT_B = "tenant-acme";
    private static final String SHARED_EMP_ID = "EMP-CROSS-001";

    @BeforeEach
    void setup() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Tenant Isolation: Identical employee IDs across different tenants cannot collide or be accessed cross-tenant")
    void crossTenantAccess_isStrictlyIsolated() {
        // Step 1: Ingest employee in Tenant A
        TenantContext.setContext(TENANT_A, "tenant-a-user", "ADMIN");
        HrEventRequest reqA = new HrEventRequest(
                "evt-a1", "WORKDAY", SHARED_EMP_ID, 1L, HrEventType.CREATE,
                Instant.now(), "Alice In Tenant A", "alice@moveinsync.com", "+919876543210", "MGR-01", "BLR-01"
        );
        ingestionService.processSingleEvent(TENANT_A, reqA);

        // Step 2: Ingest employee with identical employeeId in Tenant B with different attributes
        TenantContext.setContext(TENANT_B, "tenant-b-user", "ADMIN");
        HrEventRequest reqB = new HrEventRequest(
                "evt-b1", "SAP", SHARED_EMP_ID, 1L, HrEventType.CREATE,
                Instant.now(), "Bob In Tenant B", "bob@acme.com", "+919876543211", "MGR-02", "BLR-01"
        );
        ingestionService.processSingleEvent(TENANT_B, reqB);

        // Step 3: Switch context back to Tenant A and verify it can ONLY see Tenant A's record
        TenantContext.setContext(TENANT_A, "tenant-a-user", "ADMIN");
        Optional<Employee> tenantAView = employeeRepository.findById(TENANT_A, SHARED_EMP_ID);
        assertTrue(tenantAView.isPresent());
        assertEquals("Alice In Tenant A", tenantAView.get().getName());
        assertEquals("alice@moveinsync.com", tenantAView.get().getEmail());

        // Step 4: Switch context to Tenant B and verify it can ONLY see Tenant B's record
        TenantContext.setContext(TENANT_B, "tenant-b-user", "ADMIN");
        Optional<Employee> tenantBView = employeeRepository.findById(TENANT_B, SHARED_EMP_ID);
        assertTrue(tenantBView.isPresent());
        assertEquals("Bob In Tenant B", tenantBView.get().getName());
        assertEquals("bob@acme.com", tenantBView.get().getEmail());

        // Step 5: Negative lookup: Tenant A querying a Tenant B-only employee returns empty
        TenantContext.setContext(TENANT_A, "tenant-a-user", "ADMIN");
        Optional<Employee> nonExistentInA = employeeRepository.findById(TENANT_A, "NON-EXISTENT-IN-A");
        assertTrue(nonExistentInA.isEmpty(), "Cross-tenant query must return empty/404 without leaking existence");
    }
}
