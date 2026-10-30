package com.rosterguard.api.dto;

import com.rosterguard.domain.event.ProcessingOutcome;

public class HrEventRecordOutcome {
    private String eventId;
    private String employeeId;
    private Long sourceVersion;
    private ProcessingOutcome outcome;
    private String message;

    public HrEventRecordOutcome() {}

    public HrEventRecordOutcome(String eventId, String employeeId, Long sourceVersion,
                                ProcessingOutcome outcome, String message) {
        this.eventId = eventId;
        this.employeeId = employeeId;
        this.sourceVersion = sourceVersion;
        this.outcome = outcome;
        this.message = message;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public Long getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(Long sourceVersion) { this.sourceVersion = sourceVersion; }

    public ProcessingOutcome getOutcome() { return outcome; }
    public void setOutcome(ProcessingOutcome outcome) { this.outcome = outcome; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
