package com.rosterguard.application.reconciliation;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.rosterguard.api.dto.ReconciliationRunResponse;
import com.rosterguard.domain.employee.CanonicalHasher;
import com.rosterguard.domain.event.OutboxEvent;
import com.rosterguard.domain.reconciliation.ReconciliationStatus;
import com.rosterguard.infrastructure.persistence.EmployeeRepository;
import com.rosterguard.infrastructure.persistence.OutboxRepository;
import com.rosterguard.infrastructure.persistence.ReconciliationRepository;
import com.rosterguard.observability.RosterMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ReconciliationRepository reconciliationRepository;
    private final EmployeeRepository employeeRepository;
    private final OutboxRepository outboxRepository;
    private final RosterMetrics rosterMetrics;
    private final double missingThreshold;
    private final int batchSize;

    public ReconciliationService(ReconciliationRepository reconciliationRepository,
                                 EmployeeRepository employeeRepository,
                                 OutboxRepository outboxRepository,
                                 RosterMetrics rosterMetrics,
                                 @Value("${roster.reconciliation.missing-threshold:0.30}") double missingThreshold,
                                 @Value("${roster.reconciliation.batch-size:1000}") int batchSize) {
        this.reconciliationRepository = reconciliationRepository;
        this.employeeRepository = employeeRepository;
        this.outboxRepository = outboxRepository;
        this.rosterMetrics = rosterMetrics;
        this.missingThreshold = missingThreshold;
        this.batchSize = batchSize;
    }

    public ReconciliationRunResponse processNightlySnapshot(String tenantId, InputStream csvInputStream, Instant snapshotGeneratedAt) throws IOException {
        String runId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();
        Instant snapshotTime = snapshotGeneratedAt != null ? snapshotGeneratedAt : startTime;

        reconciliationRepository.createRun(runId, tenantId, snapshotTime);

        int totalRows = 0;
        int validRows = 0;
        int invalidRows = 0;

        List<ReconciliationRepository.StagingRecord> batch = new ArrayList<>(batchSize);

        try (CSVReader reader = new CSVReader(new InputStreamReader(csvInputStream, StandardCharsets.UTF_8))) {
            String[] line;
            boolean isHeader = true;

            while ((line = reader.readNext()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue; // Skip CSV header: employee_id,name,email,phone,manager_id,office_code,source_version,active
                }

                totalRows++;
                if (line.length < 7) {
                    invalidRows++;
                    continue;
                }

                String empId = line[0] != null ? line[0].trim() : "";
                String name = line[1] != null ? line[1].trim() : "";
                String email = line[2] != null ? line[2].trim() : "";
                String phone = line[3] != null ? line[3].trim() : "";
                String managerId = line[4] != null ? line[4].trim() : "";
                String officeCode = line[5] != null ? line[5].trim() : "";
                
                long version;
                try {
                    version = Long.parseLong(line[6].trim());
                } catch (NumberFormatException e) {
                    invalidRows++;
                    continue;
                }

                boolean active = line.length <= 7 || Boolean.parseBoolean(line[7].trim());

                if (empId.isEmpty() || name.isEmpty() || email.isEmpty() || officeCode.isEmpty()) {
                    invalidRows++;
                    continue;
                }

                String canonicalHash = CanonicalHasher.computeHash(name, email, phone, managerId, officeCode, active);
                batch.add(new ReconciliationRepository.StagingRecord(
                        empId, version, canonicalHash, name, email, phone, managerId, officeCode, active
                ));
                validRows++;

                if (batch.size() >= batchSize) {
                    reconciliationRepository.batchInsertStaging(runId, tenantId, batch);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                reconciliationRepository.batchInsertStaging(runId, tenantId, batch);
                batch.clear();
            }
        } catch (CsvValidationException e) {
            log.error("CSV validation error during reconciliation run {}", runId, e);
            throw new IOException("Malformed CSV format", e);
        }

        rosterMetrics.incrementReconRows(totalRows);
        rosterMetrics.incrementReconInvalid(invalidRows);

        // Calculate Safety Brake metrics
        int totalActiveBefore = employeeRepository.countActiveEmployees(tenantId);
        int missingActiveCount = reconciliationRepository.countMissingActiveEmployees(runId, tenantId);

        double missingRatio = totalActiveBefore > 0 ? (double) missingActiveCount / totalActiveBefore : 0.0;
        rosterMetrics.setLatestMissingRatio(missingRatio);

        log.info("Reconciliation diff complete for run {}: activeBefore={}, missingActive={}, ratio={}",
                runId, totalActiveBefore, missingActiveCount, missingRatio);

        // Check Safety Brake condition
        if (totalActiveBefore > 0 && missingRatio >= missingThreshold) {
            rosterMetrics.incrementSafetyBrake();
            log.warn("SAFETY BRAKE TRIGGERED for run {}: missingRatio={}% >= threshold={}%",
                    runId, String.format("%.2f", missingRatio * 100), String.format("%.2f", missingThreshold * 100));

            // Apply new and updated employees, but DO NOT deactivate missing employees!
            reconciliationRepository.applyNewAndChangedFromStaging(runId, tenantId, snapshotTime);

            reconciliationRepository.updateRunProgress(
                    runId, totalRows, validRows, invalidRows, missingActiveCount, missingRatio,
                    ReconciliationStatus.BLOCKED_REQUIRES_APPROVAL, null, null
            );

            return new ReconciliationRunResponse(
                    runId, tenantId, snapshotTime, totalRows, validRows, invalidRows,
                    missingActiveCount, missingRatio, ReconciliationStatus.BLOCKED_REQUIRES_APPROVAL,
                    null, startTime, null,
                    String.format("Safety brake triggered: missing employee ratio %.2f%% exceeds threshold %.2f%%. Human operator approval required before deactivating %d employees.",
                            missingRatio * 100, missingThreshold * 100, missingActiveCount)
            );
        }

        // Within safe threshold: Execute updates and automatic deactivations
        executeApprovedDeactivations(runId, tenantId, snapshotTime);

        Instant completedTime = Instant.now();
        reconciliationRepository.updateRunProgress(
                runId, totalRows, validRows, invalidRows, missingActiveCount, missingRatio,
                ReconciliationStatus.COMPLETED, "AUTO_WITHIN_THRESHOLD", completedTime
        );

        return new ReconciliationRunResponse(
                runId, tenantId, snapshotTime, totalRows, validRows, invalidRows,
                missingActiveCount, missingRatio, ReconciliationStatus.COMPLETED,
                "AUTO_WITHIN_THRESHOLD", startTime, completedTime,
                "Reconciliation completed successfully"
        );
    }

    @Transactional
    public ReconciliationRunResponse approveBlockedRun(String tenantId, String runId, String operatorUsername) {
        Optional<ReconciliationRepository.ReconciliationRun> runOpt = reconciliationRepository.findRunById(tenantId, runId);
        if (runOpt.isEmpty()) {
            throw new IllegalArgumentException("Reconciliation run not found");
        }

        ReconciliationRepository.ReconciliationRun run = runOpt.get();
        if (run.status() != ReconciliationStatus.BLOCKED_REQUIRES_APPROVAL) {
            throw new IllegalStateException("Run " + runId + " is not currently blocked (current status: " + run.status() + ")");
        }

        executeApprovedDeactivations(runId, tenantId, run.snapshotGeneratedAt());

        Instant now = Instant.now();
        reconciliationRepository.updateRunProgress(
                runId, run.totalRows(), run.validRows(), run.invalidRows(),
                run.missingEmployees(), run.missingRatio(),
                ReconciliationStatus.APPROVED_EXECUTED, operatorUsername, now
        );

        log.info("Operator {} approved blocked reconciliation run {}", operatorUsername, runId);

        return new ReconciliationRunResponse(
                runId, tenantId, run.snapshotGeneratedAt(), run.totalRows(), run.validRows(), run.invalidRows(),
                run.missingEmployees(), run.missingRatio(), ReconciliationStatus.APPROVED_EXECUTED,
                operatorUsername, run.startedAt(), now,
                "Run approved by operator; missing employees successfully deactivated"
        );
    }

    public Optional<ReconciliationRunResponse> getRunDetails(String tenantId, String runId) {
        return reconciliationRepository.findRunById(tenantId, runId)
                .map(r -> new ReconciliationRunResponse(
                        r.runId(), r.tenantId(), r.snapshotGeneratedAt(),
                        r.totalRows(), r.validRows(), r.invalidRows(),
                        r.missingEmployees(), r.missingRatio(),
                        r.status(), r.operatorApproval(),
                        r.startedAt(), r.completedAt(), "Run details"
                ));
    }

    private void executeApprovedDeactivations(String runId, String tenantId, Instant snapshotTime) {
        // Apply inserts & updates
        reconciliationRepository.applyNewAndChangedFromStaging(runId, tenantId, snapshotTime);

        // Find missing active employees and create outbox events before deactivation
        List<String> missingIds = reconciliationRepository.findMissingActiveEmployeeIds(runId, tenantId);
        Instant now = Instant.now();

        for (String empId : missingIds) {
            String eventId = UUID.randomUUID().toString();
            String payload = String.format("{\"tenantId\":\"%s\",\"employeeId\":\"%s\",\"eventType\":\"EmployeeDeactivated\",\"reason\":\"RECONCILIATION_MISSING\"}",
                    tenantId, empId);
            OutboxEvent outboxEvent = new OutboxEvent(
                    eventId, tenantId, empId, "EmployeeDeactivated", payload,
                    "PENDING", 0, now, now, null, null
            );
            outboxRepository.insert(outboxEvent);
        }

        // Deactivate missing employees in database
        reconciliationRepository.deactivateMissingEmployees(runId, tenantId, snapshotTime);
    }
}
