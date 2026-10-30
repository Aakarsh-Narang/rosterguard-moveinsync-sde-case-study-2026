package com.rosterguard.api.controller;

import com.rosterguard.api.dto.ReconciliationRunResponse;
import com.rosterguard.application.reconciliation.ReconciliationService;
import com.rosterguard.domain.reconciliation.ReconciliationStatus;
import com.rosterguard.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/reconciliations")
@Tag(name = "Nightly Reconciliation", description = "Streaming bulk CSV reconciliation and safety brake approval API")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload nightly employee CSV snapshot for streaming reconciliation")
    public ResponseEntity<ReconciliationRunResponse> uploadSnapshot(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "snapshotGeneratedAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant snapshotGeneratedAt) throws IOException {

        String tenantId = TenantContext.getTenantId();
        try (InputStream inputStream = file.getInputStream()) {
            ReconciliationRunResponse response = reconciliationService.processNightlySnapshot(
                    tenantId, inputStream, snapshotGeneratedAt != null ? snapshotGeneratedAt : Instant.now());

            if (response.getStatus() == ReconciliationStatus.BLOCKED_REQUIRES_APPROVAL) {
                // Return 202 Accepted with explanation that run is held by safety brake
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            }
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/{runId}")
    @Operation(summary = "Get reconciliation run status and error metrics")
    public ResponseEntity<ReconciliationRunResponse> getRunStatus(@PathVariable String runId) {
        String tenantId = TenantContext.getTenantId();
        return reconciliationService.getRunDetails(tenantId, runId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{runId}/approve")
    @Operation(summary = "Operator approval for safety-brake blocked reconciliation run")
    public ResponseEntity<ReconciliationRunResponse> approveRun(@PathVariable String runId) {
        String tenantId = TenantContext.getTenantId();
        String operatorUsername = TenantContext.getUsername();
        try {
            ReconciliationRunResponse response = reconciliationService.approveBlockedRun(tenantId, runId, operatorUsername);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
