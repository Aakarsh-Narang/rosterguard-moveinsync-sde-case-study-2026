package com.rosterguard.api.controller;

import com.rosterguard.api.dto.HrBatchEventResponse;
import com.rosterguard.api.dto.HrEventRecordOutcome;
import com.rosterguard.api.dto.HrEventRequest;
import com.rosterguard.application.ingestion.EmployeeIngestionService;
import com.rosterguard.domain.event.ProcessingOutcome;
import com.rosterguard.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hr/events")
@Tag(name = "HR Ingestion", description = "Real-time and batch HR event synchronization API")
public class HrEventController {

    private final EmployeeIngestionService ingestionService;

    public HrEventController(EmployeeIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    @Operation(summary = "Ingest single real-time HR event")
    public ResponseEntity<HrEventRecordOutcome> ingestSingleEvent(@Valid @RequestBody HrEventRequest request) {
        String tenantId = TenantContext.getTenantId();
        HrEventRecordOutcome outcome = ingestionService.processSingleEvent(tenantId, request);

        if (outcome.getOutcome() == ProcessingOutcome.VERSION_CONFLICT) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(outcome);
        }
        if (outcome.getOutcome() == ProcessingOutcome.REJECTED) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(outcome);
        }
        return ResponseEntity.ok(outcome);
    }

    @PostMapping("/batch")
    @Operation(summary = "Ingest batch HR events with independent record-level outcomes")
    public ResponseEntity<HrBatchEventResponse> ingestBatchEvents(@Valid @RequestBody List<HrEventRequest> requests) {
        String tenantId = TenantContext.getTenantId();
        HrBatchEventResponse response = ingestionService.processBatch(tenantId, requests);
        return ResponseEntity.ok(response);
    }
}
