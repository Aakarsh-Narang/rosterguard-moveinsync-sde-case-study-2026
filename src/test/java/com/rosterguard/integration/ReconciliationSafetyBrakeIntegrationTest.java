package com.rosterguard.integration;

import com.rosterguard.api.dto.HrEventRequest;
import com.rosterguard.api.dto.ReconciliationRunResponse;
import com.rosterguard.application.ingestion.EmployeeIngestionService;
import com.rosterguard.application.reconciliation.ReconciliationService;
import com.rosterguard.domain.event.HrEventType;
import com.rosterguard.domain.reconciliation.ReconciliationStatus;
import com.rosterguard.infrastructure.persistence.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReconciliationSafetyBrakeIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EmployeeIngestionService ingestionService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ReconciliationService reconciliationService;

    private static final String TENANT_ID = "tenant-moveinsync";

    @BeforeEach
    void setup() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Mandatory Safety Brake Test: 40% missing snapshot halts automatic deactivation until operator approval")
    void missingRatioAboveThreshold_triggersSafetyBrake() throws Exception {
        // Step 1: Seed 10 active employees in store
        for (int i = 1; i <= 10; i++) {
            String empId = "EMP-SAFETY-" + String.format("%03d", i);
            HrEventRequest req = new HrEventRequest(
                    "evt-" + empId, "WORKDAY", empId, 1L, HrEventType.CREATE,
                    Instant.now(), "Employee " + i, "emp" + i + "@moveinsync.com", "+919876543210", "MGR-01", "BLR-01"
            );
            ingestionService.processSingleEvent(TENANT_ID, req);
        }
        assertEquals(10, employeeRepository.countActiveEmployees(TENANT_ID));

        // Step 2: Nightly snapshot CSV arrives, but only contains 6 of the 10 employees (4 missing = 40% missing ratio)
        // Threshold configured in application-test.yml is 0.30 (30%)
        StringBuilder csvBuilder = new StringBuilder();
        csvBuilder.append("employee_id,name,email,phone,manager_id,office_code,source_version,active\n");
        for (int i = 1; i <= 6; i++) {
            String empId = "EMP-SAFETY-" + String.format("%03d", i);
            csvBuilder.append(String.format("%s,Employee %d,emp%d@moveinsync.com,+919876543210,MGR-01,BLR-01,2,true\n",
                    empId, i, i));
        }

        InputStream csvStream = new ByteArrayInputStream(csvBuilder.toString().getBytes(StandardCharsets.UTF_8));
        ReconciliationRunResponse response = reconciliationService.processNightlySnapshot(TENANT_ID, csvStream, Instant.now());

        // Step 3: Verify Safety Brake was triggered!
        assertEquals(ReconciliationStatus.BLOCKED_REQUIRES_APPROVAL, response.getStatus(),
                "Reconciliation status must be BLOCKED_REQUIRES_APPROVAL");
        assertEquals(4, response.getMissingEmployees());
        assertEquals(0.40, response.getMissingRatio(), 0.001);
        assertTrue(response.getMessage().contains("Safety brake triggered"));

        // Step 4: Verify ZERO employees were deactivated! All 10 must remain ACTIVE!
        int activeCountAfterRun = employeeRepository.countActiveEmployees(TENANT_ID);
        assertEquals(10, activeCountAfterRun, "Safety brake MUST protect missing employees from automatic deactivation");

        // Step 5: Authenticated operator reviews discrepancy and approves the run
        ReconciliationRunResponse approvedResponse = reconciliationService.approveBlockedRun(
                TENANT_ID, response.getRunId(), "ops-engineer-01"
        );
        assertEquals(ReconciliationStatus.APPROVED_EXECUTED, approvedResponse.getStatus());
        assertEquals("ops-engineer-01", approvedResponse.getOperatorApproval());

        // Step 6: Verify after approval, the 4 missing employees are now safely deactivated (6 remaining active)
        int finalActiveCount = employeeRepository.countActiveEmployees(TENANT_ID);
        assertEquals(6, finalActiveCount, "After operator approval, missing employees should be deactivated");
    }
}
