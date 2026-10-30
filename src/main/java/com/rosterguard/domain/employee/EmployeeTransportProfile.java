package com.rosterguard.domain.employee;

import java.time.Instant;
import java.util.Objects;

public class EmployeeTransportProfile {
    private String tenantId;
    private String employeeId;
    private String preferredPickupPoint;
    private String specialAssistanceNeeds;
    private Instant updatedAt;

    public EmployeeTransportProfile() {}

    public EmployeeTransportProfile(String tenantId, String employeeId, String preferredPickupPoint,
                                    String specialAssistanceNeeds, Instant updatedAt) {
        this.tenantId = tenantId;
        this.employeeId = employeeId;
        this.preferredPickupPoint = preferredPickupPoint;
        this.specialAssistanceNeeds = specialAssistanceNeeds;
        this.updatedAt = updatedAt;
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getPreferredPickupPoint() { return preferredPickupPoint; }
    public void setPreferredPickupPoint(String preferredPickupPoint) { this.preferredPickupPoint = preferredPickupPoint; }

    public String getSpecialAssistanceNeeds() { return specialAssistanceNeeds; }
    public void setSpecialAssistanceNeeds(String specialAssistanceNeeds) { this.specialAssistanceNeeds = specialAssistanceNeeds; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmployeeTransportProfile that)) return false;
        return Objects.equals(tenantId, that.tenantId) &&
               Objects.equals(employeeId, that.employeeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, employeeId);
    }
}
