package com.rosterguard.domain.event;

public enum ProcessingOutcome {
    APPLIED,
    DUPLICATE,
    STALE,
    REJECTED,
    VERSION_CONFLICT,
    FAILED_RETRYABLE
}
