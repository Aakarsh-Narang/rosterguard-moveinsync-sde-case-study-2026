package com.rosterguard.application.idempotency;

import com.rosterguard.domain.employee.Employee;
import com.rosterguard.domain.event.ProcessingOutcome;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class VersionManagementPolicy {

    public record PolicyDecision(ProcessingOutcome outcome, String reason) {}

    public PolicyDecision evaluate(Optional<Employee> existingEmployee,
                                   long incomingVersion,
                                   String incomingPayloadHash) {
        if (existingEmployee.isEmpty()) {
            return new PolicyDecision(ProcessingOutcome.APPLIED, "New employee creation");
        }

        Employee current = existingEmployee.get();
        long storedVersion = current.getSourceVersion();
        String storedHash = current.getPayloadHash();

        if (incomingVersion > storedVersion) {
            return new PolicyDecision(ProcessingOutcome.APPLIED, "Newer version applied");
        } else if (incomingVersion < storedVersion) {
            return new PolicyDecision(ProcessingOutcome.STALE,
                    "Incoming version " + incomingVersion + " is older than stored version " + storedVersion);
        } else {
            // Same version
            if (storedHash != null && storedHash.equalsIgnoreCase(incomingPayloadHash)) {
                return new PolicyDecision(ProcessingOutcome.DUPLICATE,
                        "Duplicate event: same version and payload hash");
            } else {
                return new PolicyDecision(ProcessingOutcome.VERSION_CONFLICT,
                        "Version conflict: same version " + incomingVersion + " with conflicting payload");
            }
        }
    }
}
