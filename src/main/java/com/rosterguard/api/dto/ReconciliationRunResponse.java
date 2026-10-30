package com.rosterguard.api.dto;

import com.rosterguard.domain.reconciliation.ReconciliationStatus;
import java.time.Instant;

public class ReconciliationRunResponse {
    private String runId;
    private String tenantId;
    private Instant snapshotGeneratedAt;
    private int totalRows;
    private int validRows;
    private int invalidRows;
    private int missingEmployees;
    private double missingRatio;
    private ReconciliationStatus status;
    private String operatorApproval;
    private Instant startedAt;
    private Instant completedAt;
    private String message;

    public ReconciliationRunResponse() {}

    public ReconciliationRunResponse(String runId, String tenantId, Instant snapshotGeneratedAt,
                                     int totalRows, int validRows, int invalidRows,
                                     int missingEmployees, double missingRatio,
                                     ReconciliationStatus status, String operatorApproval,
                                     Instant startedAt, Instant completedAt, String message) {
        this.runId = runId;
        this.tenantId = tenantId;
        this.snapshotGeneratedAt = snapshotGeneratedAt;
        this.totalRows = totalRows;
        this.validRows = validRows;
        this.invalidRows = invalidRows;
        this.missingEmployees = missingEmployees;
        this.missingRatio = missingRatio;
        this.status = status;
        this.operatorApproval = operatorApproval;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.message = message;
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Instant getSnapshotGeneratedAt() { return snapshotGeneratedAt; }
    public void setSnapshotGeneratedAt(Instant snapshotGeneratedAt) { this.snapshotGeneratedAt = snapshotGeneratedAt; }

    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }

    public int getValidRows() { return validRows; }
    public void setValidRows(int validRows) { this.validRows = validRows; }

    public int getInvalidRows() { return invalidRows; }
    public void setInvalidRows(int invalidRows) { this.invalidRows = invalidRows; }

    public int getMissingEmployees() { return missingEmployees; }
    public void setMissingEmployees(int missingEmployees) { this.missingEmployees = missingEmployees; }

    public double getMissingRatio() { return missingRatio; }
    public void setMissingRatio(double missingRatio) { this.missingRatio = missingRatio; }

    public ReconciliationStatus getStatus() { return status; }
    public void setStatus(ReconciliationStatus status) { this.status = status; }

    public String getOperatorApproval() { return operatorApproval; }
    public void setOperatorApproval(String operatorApproval) { this.operatorApproval = operatorApproval; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
