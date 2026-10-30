package com.rosterguard.application.ingestion;

import com.rosterguard.api.dto.HrEventRequest;
import com.rosterguard.domain.event.HrEventType;
import com.rosterguard.infrastructure.cache.ReferenceDataCache;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class EventValidationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{7,14}$");
    private static final Pattern EMPLOYEE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{2,64}$");

    private final ReferenceDataCache referenceDataCache;

    public EventValidationService(ReferenceDataCache referenceDataCache) {
        this.referenceDataCache = referenceDataCache;
    }

    public Optional<String> validate(String tenantId, HrEventRequest request) {
        if (request == null) {
            return Optional.of("Event payload cannot be null");
        }
        if (request.getEventId() == null || request.getEventId().isBlank()) {
            return Optional.of("eventId is required");
        }
        if (request.getSourceSystem() == null || request.getSourceSystem().isBlank()) {
            return Optional.of("sourceSystem is required");
        }
        if (request.getEmployeeId() == null || !EMPLOYEE_ID_PATTERN.matcher(request.getEmployeeId()).matches()) {
            return Optional.of("Invalid employeeId format (must be alphanumeric, 2-64 chars)");
        }
        if (request.getSourceVersion() == null || request.getSourceVersion() < 1) {
            return Optional.of("sourceVersion must be a positive integer");
        }
        if (request.getEventType() == null) {
            return Optional.of("eventType is required");
        }

        // For CREATE and UPDATE, validate contact & office fields
        if (request.getEventType() != HrEventType.DEACTIVATE) {
            if (request.getName() == null || request.getName().trim().length() < 2) {
                return Optional.of("name is required (minimum 2 characters)");
            }
            if (request.getEmail() == null || !EMAIL_PATTERN.matcher(request.getEmail().trim()).matches()) {
                return Optional.of("Invalid email format");
            }
            if (request.getPhone() != null && !request.getPhone().isBlank()) {
                if (!PHONE_PATTERN.matcher(request.getPhone().trim()).matches()) {
                    return Optional.of("Invalid phone format (must follow E.164 phone specification)");
                }
            }
            if (request.getOfficeCode() == null || request.getOfficeCode().isBlank()) {
                return Optional.of("officeCode is required");
            }
            if (!referenceDataCache.isValidOffice(tenantId, request.getOfficeCode())) {
                return Optional.of("Unknown officeCode: office not registered for tenant");
            }
        }

        return Optional.empty(); // Valid
    }
}
