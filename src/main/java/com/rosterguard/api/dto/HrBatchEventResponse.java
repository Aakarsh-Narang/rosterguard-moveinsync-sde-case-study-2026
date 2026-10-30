package com.rosterguard.api.dto;

import java.util.List;

public class HrBatchEventResponse {
    private int totalEvents;
    private int appliedCount;
    private int duplicateCount;
    private int staleCount;
    private int rejectedCount;
    private int conflictCount;
    private List<HrEventRecordOutcome> outcomes;

    public HrBatchEventResponse() {}

    public HrBatchEventResponse(int totalEvents, int appliedCount, int duplicateCount,
                                int staleCount, int rejectedCount, int conflictCount,
                                List<HrEventRecordOutcome> outcomes) {
        this.totalEvents = totalEvents;
        this.appliedCount = appliedCount;
        this.duplicateCount = duplicateCount;
        this.staleCount = staleCount;
        this.rejectedCount = rejectedCount;
        this.conflictCount = conflictCount;
        this.outcomes = outcomes;
    }

    public int getTotalEvents() { return totalEvents; }
    public void setTotalEvents(int totalEvents) { this.totalEvents = totalEvents; }

    public int getAppliedCount() { return appliedCount; }
    public void setAppliedCount(int appliedCount) { this.appliedCount = appliedCount; }

    public int getDuplicateCount() { return duplicateCount; }
    public void setDuplicateCount(int duplicateCount) { this.duplicateCount = duplicateCount; }

    public int getStaleCount() { return staleCount; }
    public void setStaleCount(int staleCount) { this.staleCount = staleCount; }

    public int getRejectedCount() { return rejectedCount; }
    public void setRejectedCount(int rejectedCount) { this.rejectedCount = rejectedCount; }

    public int getConflictCount() { return conflictCount; }
    public void setConflictCount(int conflictCount) { this.conflictCount = conflictCount; }

    public List<HrEventRecordOutcome> getOutcomes() { return outcomes; }
    public void setOutcomes(List<HrEventRecordOutcome> outcomes) { this.outcomes = outcomes; }
}
