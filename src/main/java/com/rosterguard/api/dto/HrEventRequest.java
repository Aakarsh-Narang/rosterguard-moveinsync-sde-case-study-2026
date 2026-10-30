package com.rosterguard.api.dto;

import com.rosterguard.domain.event.HrEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public class HrEventRequest {

    @NotBlank(message = "eventId is required")
    private String eventId;

    @NotBlank(message = "sourceSystem is required")
    private String sourceSystem;

    @NotBlank(message = "employeeId is required")
    private String employeeId;

    @NotNull(message = "sourceVersion is required")
    private Long sourceVersion;

    @NotNull(message = "eventType is required")
    private HrEventType eventType;

    private Instant occurredAt;

    private String name;
    private String email;
    private String phone;
    private String managerId;
    private String officeCode;

    public HrEventRequest() {}

    public HrEventRequest(String eventId, String sourceSystem, String employeeId,
                          Long sourceVersion, HrEventType eventType, Instant occurredAt,
                          String name, String email, String phone, String managerId, String officeCode) {
        this.eventId = eventId;
        this.sourceSystem = sourceSystem;
        this.employeeId = employeeId;
        this.sourceVersion = sourceVersion;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.managerId = managerId;
        this.officeCode = officeCode;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public Long getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(Long sourceVersion) { this.sourceVersion = sourceVersion; }

    public HrEventType getEventType() { return eventType; }
    public void setEventType(HrEventType eventType) { this.eventType = eventType; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }

    public String getOfficeCode() { return officeCode; }
    public void setOfficeCode(String officeCode) { this.officeCode = officeCode; }
}
