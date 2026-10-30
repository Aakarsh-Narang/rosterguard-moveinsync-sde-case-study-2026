package com.rosterguard.api.dto;

public class AuthResponse {
    private String token;
    private String tenantId;
    private String username;
    private String role;

    public AuthResponse() {}

    public AuthResponse(String token, String tenantId, String username, String role) {
        this.token = token;
        this.tenantId = tenantId;
        this.username = username;
        this.role = role;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
