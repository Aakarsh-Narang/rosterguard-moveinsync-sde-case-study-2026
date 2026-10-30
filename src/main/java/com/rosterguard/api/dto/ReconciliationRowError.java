package com.rosterguard.api.dto;

/**
 * Record-level validation error report for nightly CSV reconciliation rows.
 * Contains no PII / sensitive personal data.
 */
public record ReconciliationRowError(
        int rowNumber,
        String employeeId,
        String fieldName,
        String errorCode,
        String explanation
) {}
