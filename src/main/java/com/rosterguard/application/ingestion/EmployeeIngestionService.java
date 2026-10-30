package com.rosterguard.application.ingestion;

import com.rosterguard.api.dto.HrBatchEventResponse;
import com.rosterguard.api.dto.HrEventRecordOutcome;
import com.rosterguard.api.dto.HrEventRequest;
import com.rosterguard.application.idempotency.VersionManagementPolicy;
import com.rosterguard.domain.employee.CanonicalHasher;
import com.rosterguard.domain.employee.Employee;
import com.rosterguard.domain.event.HrEventType;
import com.rosterguard.domain.event.OutboxEvent;
import com.rosterguard.domain.event.ProcessingOutcome;
import com.rosterguard.infrastructure.persistence.EmployeeRepository;
import com.rosterguard.infrastructure.persistence.IdempotencyRepository;
import com.rosterguard.infrastructure.persistence.OutboxRepository;
import com.rosterguard.observability.RosterMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmployeeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeIngestionService.class);

    private final EventValidationService validationService;
    private final EmployeeRepository employeeRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final OutboxRepository outboxRepository;
    private final VersionManagementPolicy versionManagementPolicy;
    private final RosterMetrics rosterMetrics;

    public EmployeeIngestionService(EventValidationService validationService,
                                    EmployeeRepository employeeRepository,
                                    IdempotencyRepository idempotencyRepository,
                                    OutboxRepository outboxRepository,
                                    VersionManagementPolicy versionManagementPolicy,
                                    RosterMetrics rosterMetrics) {
        this.validationService = validationService;
        this.employeeRepository = employeeRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.outboxRepository = outboxRepository;
        this.versionManagementPolicy = versionManagementPolicy;
        this.rosterMetrics = rosterMetrics;
    }

    public HrEventRecordOutcome processSingleEvent(String tenantId, HrEventRequest request) {
        io.micrometer.core.instrument.Timer.Sample sample = io.micrometer.core.instrument.Timer.start(rosterMetrics.getRegistry());
        try {
            rosterMetrics.incrementReceived();

            // 1. Boundary Validation
            Optional<String> validationError = validationService.validate(tenantId, request);
            if (validationError.isPresent()) {
                rosterMetrics.incrementRejected();
                return new HrEventRecordOutcome(request.getEventId(), request.getEmployeeId(),
                        request.getSourceVersion(), ProcessingOutcome.REJECTED, validationError.get());
            }

            // 2. Transactional ingestion
            return executeTransactionalIngest(tenantId, request);
        } finally {
            sample.stop(rosterMetrics.getEventProcessingTimer());
        }
    }

    public HrBatchEventResponse processBatch(String tenantId, List<HrEventRequest> requests) {
        int applied = 0;
        int duplicate = 0;
        int stale = 0;
        int rejected = 0;
        int conflict = 0;
        List<HrEventRecordOutcome> outcomes = new ArrayList<>(requests.size());

        for (HrEventRequest req : requests) {
            HrEventRecordOutcome outcome = processSingleEvent(tenantId, req);
            outcomes.add(outcome);

            switch (outcome.getOutcome()) {
                case APPLIED -> applied++;
                case DUPLICATE -> duplicate++;
                case STALE -> stale++;
                case REJECTED -> rejected++;
                case VERSION_CONFLICT -> conflict++;
                default -> {}
            }
        }

        return new HrBatchEventResponse(requests.size(), applied, duplicate, stale, rejected, conflict, outcomes);
    }

    @Transactional
    public HrEventRecordOutcome executeTransactionalIngest(String tenantId, HrEventRequest req) {
        String employeeId = req.getEmployeeId();
        long sourceVersion = req.getSourceVersion();
        String sourceSystem = req.getSourceSystem();
        boolean isActive = req.getEventType() != HrEventType.DEACTIVATE;

        // 1. Check idempotency receipt table first
        Optional<IdempotencyRepository.IdempotencyReceipt> existingReceipt =
                idempotencyRepository.findReceipt(tenantId, sourceSystem, employeeId, sourceVersion);

        // Compute canonical hash of incoming HR payload
        String incomingHash = CanonicalHasher.computeHash(
                req.getName(), req.getEmail(), req.getPhone(),
                req.getManagerId(), req.getOfficeCode(), isActive
        );

        if (existingReceipt.isPresent()) {
            IdempotencyRepository.IdempotencyReceipt receipt = existingReceipt.get();
            if (receipt.payloadHash().equalsIgnoreCase(incomingHash)) {
                rosterMetrics.incrementDuplicate();
                return new HrEventRecordOutcome(req.getEventId(), employeeId, sourceVersion,
                        ProcessingOutcome.DUPLICATE, "Duplicate event: previously processed");
            } else {
                rosterMetrics.incrementRejected();
                return new HrEventRecordOutcome(req.getEventId(), employeeId, sourceVersion,
                        ProcessingOutcome.VERSION_CONFLICT, "Version conflict: existing receipt with conflicting payload");
            }
        }

        // 2. Acquire pessimistic lock on employee row to prevent concurrent race condition
        Optional<Employee> currentOpt = employeeRepository.findForUpdate(tenantId, employeeId);

        // 3. Evaluate version ordering policy
        VersionManagementPolicy.PolicyDecision decision =
                versionManagementPolicy.evaluate(currentOpt, sourceVersion, incomingHash);

        if (decision.outcome() == ProcessingOutcome.STALE) {
            idempotencyRepository.saveReceipt(tenantId, sourceSystem, employeeId, sourceVersion, incomingHash, ProcessingOutcome.STALE);
            rosterMetrics.incrementStale();
            log.info("Ignored stale HR event: empId={}, incomingVersion={}", employeeId, sourceVersion);
            return new HrEventRecordOutcome(req.getEventId(), employeeId, sourceVersion,
                    ProcessingOutcome.STALE, decision.reason());
        }

        if (decision.outcome() == ProcessingOutcome.VERSION_CONFLICT) {
            rosterMetrics.incrementRejected();
            log.warn("Version conflict detected for empId={}, version={}", employeeId, sourceVersion);
            return new HrEventRecordOutcome(req.getEventId(), employeeId, sourceVersion,
                    ProcessingOutcome.VERSION_CONFLICT, decision.reason());
        }

        if (decision.outcome() == ProcessingOutcome.DUPLICATE) {
            rosterMetrics.incrementDuplicate();
            return new HrEventRecordOutcome(req.getEventId(), employeeId, sourceVersion,
                    ProcessingOutcome.DUPLICATE, decision.reason());
        }

        // 4. Decision is APPLIED: Persist Employee state
        Instant eventTime = req.getOccurredAt() != null ? req.getOccurredAt() : Instant.now();
        Instant now = Instant.now();

        if (currentOpt.isEmpty()) {
            // New employee
            Employee newEmp = new Employee(
                    tenantId, employeeId, req.getName(), req.getEmail(), req.getPhone(),
                    req.getManagerId(), req.getOfficeCode(), sourceSystem, sourceVersion,
                    incomingHash, isActive, eventTime, now
            );
            try {
                employeeRepository.insert(newEmp);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // Another thread concurrently inserted the employee!
                Optional<Employee> recheck = employeeRepository.findForUpdate(tenantId, employeeId);
                if (recheck.isPresent()) {
                    long currentVer = recheck.get().getSourceVersion();
                    if (currentVer > sourceVersion) {
                        idempotencyRepository.saveReceipt(tenantId, sourceSystem, employeeId, sourceVersion, incomingHash, ProcessingOutcome.STALE);
                        rosterMetrics.incrementStale();
                        return new HrEventRecordOutcome(req.getEventId(), employeeId, sourceVersion,
                                ProcessingOutcome.STALE, "Concurrent version is newer");
                    } else if (currentVer == sourceVersion) {
                        rosterMetrics.incrementDuplicate();
                        return new HrEventRecordOutcome(req.getEventId(), employeeId, sourceVersion,
                                ProcessingOutcome.DUPLICATE, "Duplicate concurrent creation");
                    }
                }
                rosterMetrics.incrementDuplicate();
                return new HrEventRecordOutcome(req.getEventId(), employeeId, sourceVersion,
                        ProcessingOutcome.DUPLICATE, "Duplicate concurrent creation");
            }
        } else {
            // Existing employee update or deactivation
            Employee existing = currentOpt.get();
            existing.setName(req.getName() != null ? req.getName() : existing.getName());
            existing.setEmail(req.getEmail() != null ? req.getEmail() : existing.getEmail());
            existing.setPhone(req.getPhone() != null ? req.getPhone() : existing.getPhone());
            existing.setManagerId(req.getManagerId() != null ? req.getManagerId() : existing.getManagerId());
            existing.setOfficeCode(req.getOfficeCode() != null ? req.getOfficeCode() : existing.getOfficeCode());
            existing.setSourceSystem(sourceSystem);
            existing.setSourceVersion(sourceVersion);
            existing.setPayloadHash(incomingHash);
            existing.setActive(isActive);
            existing.setLastHrUpdateAt(eventTime);
            existing.setUpdatedAt(now);
            
            boolean updated = employeeRepository.update(existing);
            if (!updated) {
                // Database guard: a newer version was committed by a concurrent thread!
                idempotencyRepository.saveReceipt(tenantId, sourceSystem, employeeId, sourceVersion, incomingHash, ProcessingOutcome.STALE);
                rosterMetrics.incrementStale();
                return new HrEventRecordOutcome(req.getEventId(), employeeId, sourceVersion,
                        ProcessingOutcome.STALE, "Update rejected: stored version is newer");
            }
        }

        // 5. Save Idempotency Receipt
        idempotencyRepository.saveReceipt(tenantId, sourceSystem, employeeId, sourceVersion, incomingHash, ProcessingOutcome.APPLIED);

        // 6. Enqueue Outbox Event
        String outboxEventType = switch (req.getEventType()) {
            case CREATE -> "EmployeeCreated";
            case UPDATE -> "EmployeeUpdated";
            case DEACTIVATE -> "EmployeeDeactivated";
        };

        String outboxEventId = UUID.randomUUID().toString();
        String outboxPayload = String.format("{\"tenantId\":\"%s\",\"employeeId\":\"%s\",\"sourceVersion\":%d,\"eventType\":\"%s\"}",
                tenantId, employeeId, sourceVersion, outboxEventType);

        OutboxEvent outboxEvent = new OutboxEvent(
                outboxEventId, tenantId, employeeId, outboxEventType, outboxPayload,
                "PENDING", 0, now, now, null, null
        );
        outboxRepository.insert(outboxEvent);

        rosterMetrics.incrementApplied();
        log.info("Successfully applied HR event: empId={}, version={}, type={}",
                employeeId, sourceVersion, outboxEventType);

        return new HrEventRecordOutcome(req.getEventId(), employeeId, sourceVersion,
                ProcessingOutcome.APPLIED, "Successfully applied HR event");
    }
}
