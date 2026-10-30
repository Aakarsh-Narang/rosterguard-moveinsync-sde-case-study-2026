package com.rosterguard.unit;

import com.rosterguard.api.dto.HrEventRequest;
import com.rosterguard.application.ingestion.EventValidationService;
import com.rosterguard.domain.event.HrEventType;
import com.rosterguard.infrastructure.cache.ReferenceDataCache;
import com.rosterguard.infrastructure.persistence.OfficeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EventValidationServiceTest {

    private EventValidationService validationService;

    @BeforeEach
    void setUp() {
        OfficeRepository stubOfficeRepo = new OfficeRepository(null) {
            @Override
            public boolean exists(String tenantId, String officeCode) {
                return "BLR-01".equalsIgnoreCase(officeCode);
            }
        };

        ReferenceDataCache cache = new ReferenceDataCache(stubOfficeRepo);
        validationService = new EventValidationService(cache);
    }

    @Test
    @DisplayName("Valid event returns empty error")
    void validEvent_returnsEmpty() {
        HrEventRequest request = new HrEventRequest(
                "evt-100", "WORKDAY", "EMP-101", 1L, HrEventType.CREATE,
                null, "Alice Developer", "alice@example.com", "+919876543210", "MGR-01", "BLR-01"
        );

        Optional<String> error = validationService.validate("tenant-1", request);
        assertTrue(error.isEmpty(), "Expected valid event without error");
    }

    @Test
    @DisplayName("Malformed email is rejected without leaking personal data")
    void malformedEmail_isRejected() {
        HrEventRequest request = new HrEventRequest(
                "evt-101", "WORKDAY", "EMP-101", 1L, HrEventType.CREATE,
                null, "Alice", "not-a-valid-email", "+919876543210", "MGR-01", "BLR-01"
        );

        Optional<String> error = validationService.validate("tenant-1", request);
        assertTrue(error.isPresent());
        assertEquals("Invalid email format", error.get());
        assertFalse(error.get().contains("not-a-valid-email"), "Error message should not leak personal data");
    }

    @Test
    @DisplayName("Malformed phone number is rejected")
    void malformedPhone_isRejected() {
        HrEventRequest request = new HrEventRequest(
                "evt-102", "WORKDAY", "EMP-101", 1L, HrEventType.CREATE,
                null, "Alice", "alice@example.com", "abc-1234", "MGR-01", "BLR-01"
        );

        Optional<String> error = validationService.validate("tenant-1", request);
        assertTrue(error.isPresent());
        assertTrue(error.get().contains("phone specification"));
    }

    @Test
    @DisplayName("Unknown office code is rejected")
    void unknownOffice_isRejected() {
        HrEventRequest request = new HrEventRequest(
                "evt-103", "WORKDAY", "EMP-101", 1L, HrEventType.CREATE,
                null, "Alice", "alice@example.com", "+919876543210", "MGR-01", "UNKNOWN-OFFICE"
        );

        Optional<String> error = validationService.validate("tenant-1", request);
        assertTrue(error.isPresent());
        assertTrue(error.get().contains("Unknown officeCode"));
    }

    @Test
    @DisplayName("Deactivate event succeeds even without office or email")
    void deactivateEvent_doesNotRequireContactFields() {
        HrEventRequest request = new HrEventRequest(
                "evt-104", "WORKDAY", "EMP-101", 5L, HrEventType.DEACTIVATE,
                null, null, null, null, null, null
        );

        Optional<String> error = validationService.validate("tenant-1", request);
        assertTrue(error.isEmpty());
    }
}
