package com.rosterguard.domain.employee;

import java.time.Instant;
import java.util.Objects;

public class Employee {
    private String tenantId;
    private String employeeId;
    private String name;
    private String email;
    private String phone;
    private String managerId;
    private String officeCode;
    private String sourceSystem;
    private long sourceVersion;
    private String payloadHash;
    private boolean active;
    private Instant lastHrUpdateAt;
    private Instant updatedAt;

    public Employee() {}

    public Employee(String tenantId, String employeeId, String name, String email, String phone,
                    String managerId, String officeCode, String sourceSystem, long sourceVersion,
                    String payloadHash, boolean active, Instant lastHrUpdateAt, Instant updatedAt) {
        this.tenantId = tenantId;
        this.employeeId = employeeId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.managerId = managerId;
        this.officeCode = officeCode;
        this.sourceSystem = sourceSystem;
        this.sourceVersion = sourceVersion;
        this.payloadHash = payloadHash;
        this.active = active;
        this.lastHrUpdateAt = lastHrUpdateAt;
        this.updatedAt = updatedAt;
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

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

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

    public long getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(long sourceVersion) { this.sourceVersion = sourceVersion; }

    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getLastHrUpdateAt() { return lastHrUpdateAt; }
    public void setLastHrUpdateAt(Instant lastHrUpdateAt) { this.lastHrUpdateAt = lastHrUpdateAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Employee employee)) return false;
        return Objects.equals(tenantId, employee.tenantId) &&
               Objects.equals(employeeId, employee.employeeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, employeeId);
    }
}
