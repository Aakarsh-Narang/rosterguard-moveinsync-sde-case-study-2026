package com.rosterguard.integration;

import com.rosterguard.api.dto.HrEventRequest;
import com.rosterguard.application.ingestion.EmployeeIngestionService;
import com.rosterguard.domain.employee.EmployeeTransportProfile;
import com.rosterguard.domain.event.HrEventType;
import com.rosterguard.infrastructure.persistence.EmployeeTransportProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FieldOwnershipIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EmployeeIngestionService ingestionService;

    @Autowired
    private EmployeeTransportProfileRepository profileRepository;

    private static final String TENANT_ID = "tenant-moveinsync";
    private static final String EMP_ID = "EMP-OWN-001";

    @BeforeEach
    void setup() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Mandatory Field Ownership: HR updates never overwrite transport-admin preferences")
    void hrUpdate_neverOverwritesTransportPreferences() {
        // Step 1: Create initial employee via HR event
        HrEventRequest createReq = new HrEventRequest(
                "evt-1", "WORKDAY", EMP_ID, 1L, HrEventType.CREATE,
                Instant.now(), "Bob Commuter", "bob@moveinsync.com", "+919876543210", "MGR-01", "BLR-01"
        );
        ingestionService.processSingleEvent(TENANT_ID, createReq);

        // Step 2: Transport Admin sets preferred pickup point & special assistance
        EmployeeTransportProfile initialProfile = new EmployeeTransportProfile(
                TENANT_ID, EMP_ID, "Gate 3 North Plaza", "Ramp assistance required", Instant.now()
        );
        profileRepository.upsert(initialProfile);

        // Verify initial transport profile
        Optional<EmployeeTransportProfile> storedProfile = profileRepository.findById(TENANT_ID, EMP_ID);
        assertTrue(storedProfile.isPresent());
        assertEquals("Gate 3 North Plaza", storedProfile.get().getPreferredPickupPoint());
        assertEquals("Ramp assistance required", storedProfile.get().getSpecialAssistanceNeeds());

        // Step 3: Send HR update changing office to HYD-01 and updating email
        HrEventRequest updateReq = new HrEventRequest(
                "evt-2", "WORKDAY", EMP_ID, 2L, HrEventType.UPDATE,
                Instant.now(), "Bob Commuter", "bob.commuter@moveinsync.com", "+919876543210", "MGR-02", "HYD-01"
        );
        ingestionService.processSingleEvent(TENANT_ID, updateReq);

        // Step 4: Verify transport preferences remain completely unchanged!
        storedProfile = profileRepository.findById(TENANT_ID, EMP_ID);
        assertTrue(storedProfile.isPresent());
        assertEquals("Gate 3 North Plaza", storedProfile.get().getPreferredPickupPoint(),
                "Pickup point must not be overwritten by HR update");
        assertEquals("Ramp assistance required", storedProfile.get().getSpecialAssistanceNeeds(),
                "Special assistance needs must not be overwritten by HR update");

        // Step 5: Change transport preference through transport admin
        EmployeeTransportProfile modifiedProfile = new EmployeeTransportProfile(
                TENANT_ID, EMP_ID, "Metro Station Pillar 42", null, Instant.now()
        );
        profileRepository.upsert(modifiedProfile);

        // Step 6: Verify later HR updates still preserve the updated transport preference
        HrEventRequest v3 = new HrEventRequest(
                "evt-3", "WORKDAY", EMP_ID, 3L, HrEventType.UPDATE,
                Instant.now(), "Bob Commuter", "bob.commuter@moveinsync.com", "+919876543210", "MGR-03", "DEL-01"
        );
        ingestionService.processSingleEvent(TENANT_ID, v3);

        storedProfile = profileRepository.findById(TENANT_ID, EMP_ID);
        assertTrue(storedProfile.isPresent());
        assertEquals("Metro Station Pillar 42", storedProfile.get().getPreferredPickupPoint(),
                "Transport preference must remain intact across subsequent HR updates");
    }
}
