package com.rosterguard.integration;

import com.rosterguard.api.dto.HrEventRecordOutcome;
import com.rosterguard.api.dto.HrEventRequest;
import com.rosterguard.application.ingestion.EmployeeIngestionService;
import com.rosterguard.domain.employee.Employee;
import com.rosterguard.domain.event.HrEventType;
import com.rosterguard.domain.event.ProcessingOutcome;
import com.rosterguard.infrastructure.persistence.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyAndOrderingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EmployeeIngestionService ingestionService;

    @Autowired
    private EmployeeRepository employeeRepository;

    private static final String TENANT_ID = "tenant-moveinsync";
    private static final String EMP_ID = "EMP-ORDER-001";

    @BeforeEach
    void setup() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Mandatory Order Test: Version 7 followed by Version 6 preserves Version 7")
    void version7FollowedByVersion6_preservesVersion7() {
        // Step 1: Submit Version 7 (Office transfer to HYD-01)
        HrEventRequest v7 = new HrEventRequest(
                "evt-v7", "WORKDAY", EMP_ID, 7L, HrEventType.UPDATE,
                Instant.now(), "Devin Archer", "devin@moveinsync.com", "+919876543210", "MGR-01", "HYD-01"
        );
        HrEventRecordOutcome outcomeV7 = ingestionService.processSingleEvent(TENANT_ID, v7);
        assertEquals(ProcessingOutcome.APPLIED, outcomeV7.getOutcome());

        // Step 2: Submit Version 6 (Earlier event arriving late, office BLR-01)
        HrEventRequest v6 = new HrEventRequest(
                "evt-v6", "WORKDAY", EMP_ID, 6L, HrEventType.UPDATE,
                Instant.now().minusSeconds(3600), "Devin Archer", "devin@moveinsync.com", "+919876543210", "MGR-01", "BLR-01"
        );
        HrEventRecordOutcome outcomeV6 = ingestionService.processSingleEvent(TENANT_ID, v6);
        assertEquals(ProcessingOutcome.STALE, outcomeV6.getOutcome(), "Expected v6 arriving after v7 to be marked STALE");

        // Verify stored state is strictly Version 7 in HYD-01
        Optional<Employee> stored = employeeRepository.findById(TENANT_ID, EMP_ID);
        assertTrue(stored.isPresent());
        assertEquals(7L, stored.get().getSourceVersion(), "Final employee version must remain 7");
        assertEquals("HYD-01", stored.get().getOfficeCode(), "Final office code must remain HYD-01 from version 7");

        // Step 3: Replay Version 7 again (Duplicate)
        HrEventRecordOutcome outcomeV7Dup = ingestionService.processSingleEvent(TENANT_ID, v7);
        assertEquals(ProcessingOutcome.DUPLICATE, outcomeV7Dup.getOutcome(), "Replaying v7 should return DUPLICATE no-op");
        
        // Final employee version is still 7
        stored = employeeRepository.findById(TENANT_ID, EMP_ID);
        assertTrue(stored.isPresent());
        assertEquals(7L, stored.get().getSourceVersion());
    }

    @Test
    @DisplayName("Same version with different payload triggers VERSION_CONFLICT")
    void sameVersionDifferentPayload_returnsConflict() {
        HrEventRequest v1A = new HrEventRequest(
                "evt-v1a", "WORKDAY", "EMP-CONFLICT-01", 1L, HrEventType.CREATE,
                Instant.now(), "Original Name", "orig@moveinsync.com", "+919876543210", "MGR-01", "BLR-01"
        );
        assertEquals(ProcessingOutcome.APPLIED, ingestionService.processSingleEvent(TENANT_ID, v1A).getOutcome());

        // Conflicting payload for same version 1
        HrEventRequest v1B = new HrEventRequest(
                "evt-v1b", "WORKDAY", "EMP-CONFLICT-01", 1L, HrEventType.CREATE,
                Instant.now(), "Diverged Name", "diverged@moveinsync.com", "+919876543210", "MGR-01", "BLR-01"
        );
        HrEventRecordOutcome outcome = ingestionService.processSingleEvent(TENANT_ID, v1B);
        assertEquals(ProcessingOutcome.VERSION_CONFLICT, outcome.getOutcome());
    }
}
