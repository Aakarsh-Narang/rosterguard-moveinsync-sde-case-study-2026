package com.rosterguard.unit;

import com.rosterguard.application.idempotency.VersionManagementPolicy;
import com.rosterguard.domain.employee.Employee;
import com.rosterguard.domain.event.ProcessingOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionManagementPolicyTest {

    private VersionManagementPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new VersionManagementPolicy();
    }

    @Test
    @DisplayName("New employee is applied")
    void newEmployee_isApplied() {
        var decision = policy.evaluate(Optional.empty(), 1L, "hash-1");
        assertEquals(ProcessingOutcome.APPLIED, decision.outcome());
    }

    @Test
    @DisplayName("Higher version applied over lower stored version")
    void higherVersion_isApplied() {
        Employee existing = new Employee(
                "tenant-1", "EMP-1", "Alice", "alice@example.com", null, null, "BLR-01",
                "WORKDAY", 5L, "hash-5", true, Instant.now(), Instant.now()
        );

        var decision = policy.evaluate(Optional.of(existing), 7L, "hash-7");
        assertEquals(ProcessingOutcome.APPLIED, decision.outcome());
    }

    @Test
    @DisplayName("Lower version arriving after higher version is detected as STALE")
    void lowerVersion_isStale() {
        Employee existing = new Employee(
                "tenant-1", "EMP-1", "Alice", "alice@example.com", null, null, "BLR-01",
                "WORKDAY", 7L, "hash-7", true, Instant.now(), Instant.now()
        );

        var decision = policy.evaluate(Optional.of(existing), 6L, "hash-6");
        assertEquals(ProcessingOutcome.STALE, decision.outcome());
    }

    @Test
    @DisplayName("Same version and same payload hash is DUPLICATE")
    void sameVersionSameHash_isDuplicate() {
        Employee existing = new Employee(
                "tenant-1", "EMP-1", "Alice", "alice@example.com", null, null, "BLR-01",
                "WORKDAY", 7L, "hash-7", true, Instant.now(), Instant.now()
        );

        var decision = policy.evaluate(Optional.of(existing), 7L, "hash-7");
        assertEquals(ProcessingOutcome.DUPLICATE, decision.outcome());
    }

    @Test
    @DisplayName("Same version with different payload hash is VERSION_CONFLICT")
    void sameVersionDifferentHash_isConflict() {
        Employee existing = new Employee(
                "tenant-1", "EMP-1", "Alice", "alice@example.com", null, null, "BLR-01",
                "WORKDAY", 7L, "hash-7", true, Instant.now(), Instant.now()
        );

        var decision = policy.evaluate(Optional.of(existing), 7L, "hash-7-diverged");
        assertEquals(ProcessingOutcome.VERSION_CONFLICT, decision.outcome());
    }
}
