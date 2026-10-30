package com.rosterguard.domain.reconciliation;

public enum ReconciliationStatus {
    PENDING,
    COMPLETED,
    BLOCKED_REQUIRES_APPROVAL,
    APPROVED_EXECUTED,
    REJECTED
}
