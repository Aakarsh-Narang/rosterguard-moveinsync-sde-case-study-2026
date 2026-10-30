package com.rosterguard.security;

public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();

    private TenantContext() {}

    public static void setContext(String tenantId, String username, String role) {
        CURRENT_TENANT.set(tenantId);
        CURRENT_USER.set(username);
        CURRENT_ROLE.set(role);
    }

    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static String getUsername() {
        return CURRENT_USER.get();
    }

    public static String getRole() {
        return CURRENT_ROLE.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_USER.remove();
        CURRENT_ROLE.remove();
    }
}
