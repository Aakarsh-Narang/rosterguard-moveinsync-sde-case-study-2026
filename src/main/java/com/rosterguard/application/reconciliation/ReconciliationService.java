package com.rosterguard.application.reconciliation;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.rosterguard.api.dto.ReconciliationRowError;
import com.rosterguard.api.dto.ReconciliationRunResponse;
import com.rosterguard.domain.employee.CanonicalHasher;
import com.rosterguard.domain.event.OutboxEvent;
import com.rosterguard.domain.reconciliation.ReconciliationStatus;
import com.rosterguard.infrastructure.cache.ReferenceDataCache;
import com.rosterguard.infrastructure.persistence.EmployeeRepository;
import com.rosterguard.infrastructure.persistence.OutboxRepository;
import com.rosterguard.infrastructure.persistence.ReconciliationRepository;
import com.rosterguard.observability.RosterMetrics;
import io.micrometer.core.instrument.Timer;
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
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{7,14}$");
    private static final Pattern EMPLOYEE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{2,64}$");

    private final ReconciliationRepository reconciliationRepository;
    private final EmployeeRepository employeeRepository;
    private final OutboxRepository outboxRepository;
    private final ReferenceDataCache referenceDataCache;
    private final RosterMetrics rosterMetrics;
    private final double missingThreshold;
    private final int batchSize;

    public ReconciliationService(ReconciliationRepository reconciliationRepository,
                                 EmployeeRepository employeeRepository,
                                 OutboxRepository outboxRepository,
                                 ReferenceDataCache referenceDataCache,
                                 RosterMetrics rosterMetrics,
                                 @Value("${roster.reconciliation.missing-threshold:0.30}") double missingThreshold,
                                 @Value("${roster.reconciliation.batch-size:1000}") int batchSize) {
        this.reconciliationRepository = reconciliationRepository;
        this.employeeRepository = employeeRepository;
        this.outboxRepository = outboxRepository;
        this.referenceDataCache = referenceDataCache;
        this.rosterMetrics = rosterMetrics;
        this.missingThreshold = missingThreshold;
        this.batchSize = batchSize;
    }

    public ReconciliationRunResponse processNightlySnapshot(String tenantId, InputStream csvInputStream, Instant snapshotGeneratedAt) throws IOException {
        Timer.Sample timerSample = Timer.start(rosterMetrics.getRegistry());
        String runId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();
        Instant snapshotTime = snapshotGeneratedAt != null ? snapshotGeneratedAt : startTime;

        reconciliationRepository.createRun(runId, tenantId, snapshotTime);

        int totalRows = 0;
        int validRows = 0;
        int invalidRows = 0;

        List<ReconciliationRepository.StagingRecord> batch = new ArrayList<>(batchSize);
        List<ReconciliationRowError> sampleErrors = new ArrayList<>(100);
        Set<String> seenEmployeeIds = new HashSet<>();

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
                    if (sampleErrors.size() < 100) {
                        sampleErrors.add(new ReconciliationRowError(totalRows, "UNKNOWN", "line", "INSUFFICIENT_COLUMNS", "CSV line contains fewer than 7 required columns"));
                    }
                    continue;
                }

                String empId = line[0] != null ? line[0].trim() : "";
                String name = line[1] != null ? line[1].trim() : "";
                String email = line[2] != null ? line[2].trim() : "";
                String phone = line[3] != null ? line[3].trim() : "";
                String managerId = line[4] != null ? line[4].trim() : "";
                String officeCode = line[5] != null ? line[5].trim() : "";

                // 1. Employee ID Validation
                if (empId.isEmpty() || !EMPLOYEE_ID_PATTERN.matcher(empId).matches()) {
                    invalidRows++;
                    if (sampleErrors.size() < 100) {
                        sampleErrors.add(new ReconciliationRowError(totalRows, empId.isEmpty() ? "MISSING" : empId, "employee_id", "INVALID_ID_FORMAT", "Employee ID must be alphanumeric (2-64 chars)"));
                    }
                    continue;
                }

                // 2. Duplicate Employee ID in Same Snapshot Check
                if (!seenEmployeeIds.add(empId)) {
                    invalidRows++;
                    if (sampleErrors.size() < 100) {
                        sampleErrors.add(new ReconciliationRowError(totalRows, empId, "employee_id", "DUPLICATE_IN_SNAPSHOT", "Duplicate employee ID encountered within the same snapshot"));
                    }
                    continue;
                }

                // 3. Name Validation
                if (name.length() < 2) {
                    invalidRows++;
                    if (sampleErrors.size() < 100) {
                        sampleErrors.add(new ReconciliationRowError(totalRows, empId, "name", "INVALID_NAME", "Name must contain at least 2 characters"));
                    }
                    continue;
                }

                // 4. Email Format Validation
                if (email.isEmpty() || !EMAIL_PATTERN.matcher(email).matches()) {
                    invalidRows++;
                    if (sampleErrors.size() < 100) {
                        sampleErrors.add(new ReconciliationRowError(totalRows, empId, "email", "INVALID_EMAIL", "Email does not conform to standard email specification"));
                    }
                    continue;
                }

                // 5. Phone Format Validation (when present)
                if (!phone.isEmpty() && !PHONE_PATTERN.matcher(phone).matches()) {
                    invalidRows++;
                    if (sampleErrors.size() < 100) {
                        sampleErrors.add(new ReconciliationRowError(totalRows, empId, "phone", "INVALID_PHONE", "Phone number must follow international E.164 specification"));
                    }
                    continue;
                }

                // 6. Source Version Validation
                long version;
                try {
                    version = Long.parseLong(line[6].trim());
                    if (version < 1) {
                        throw new NumberFormatException("Negative version");
                    }
                } catch (NumberFormatException e) {
                    invalidRows++;
                    if (sampleErrors.size() < 100) {
                        sampleErrors.add(new ReconciliationRowError(totalRows, empId, "source_version", "INVALID_VERSION", "Source version must be a positive integer"));
                    }
                    continue;
                }

                // 7. Office Code Validation
                if (officeCode.isEmpty() || !referenceDataCache.isValidOffice(tenantId, officeCode)) {
                    invalidRows++;
                    if (sampleErrors.size() < 100) {
                        sampleErrors.add(new ReconciliationRowError(totalRows, empId, "office_code", "INVALID_OFFICE", "Office code not found in tenant reference directory"));
                    }
                    continue;
                }

                // 8. Active status
                boolean active = line.length <= 7 || Boolean.parseBoolean(line[7].trim());

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

        ReconciliationRunResponse response;

        // Check Safety Brake condition (>= 30% missing ratio)
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

            response = new ReconciliationRunResponse(
                    runId, tenantId, snapshotTime, totalRows, validRows, invalidRows,
                    missingActiveCount, missingRatio, ReconciliationStatus.BLOCKED_REQUIRES_APPROVAL,
                    null, startTime, null,
                    String.format("Safety brake triggered: missing employee ratio %.2f%% exceeds threshold %.2f%%. Human operator approval required before deactivating %d employees.",
                            missingRatio * 100, missingThreshold * 100, missingActiveCount)
            );
        } else {
            // Within safe threshold: Execute updates and automatic deactivations
            executeApprovedDeactivations(runId, tenantId, snapshotTime);

            Instant completedTime = Instant.now();
            reconciliationRepository.updateRunProgress(
                    runId, totalRows, validRows, invalidRows, missingActiveCount, missingRatio,
                    ReconciliationStatus.COMPLETED, "AUTO_WITHIN_THRESHOLD", completedTime
            );

            response = new ReconciliationRunResponse(
                    runId, tenantId, snapshotTime, totalRows, validRows, invalidRows,
                    missingActiveCount, missingRatio, ReconciliationStatus.COMPLETED,
                    "AUTO_WITHIN_THRESHOLD", startTime, completedTime,
                    "Reconciliation completed successfully"
            );
        }

        timerSample.stop(rosterMetrics.getReconTimer());
        response.setSampleErrors(sampleErrors);
        return response;
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

    @Transactional
    public void executeApprovedDeactivations(String runId, String tenantId, Instant snapshotTime) {
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

        // Deactivate missing employees in database atomically
        reconciliationRepository.deactivateMissingEmployees(runId, tenantId, snapshotTime);
    }
}
