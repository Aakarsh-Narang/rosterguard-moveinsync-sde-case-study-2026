package com.rosterguard.domain.employee;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class CanonicalHasher {

    private CanonicalHasher() {}

    public static String computeHash(String name, String email, String phone,
                                     String managerId, String officeCode, boolean active) {
        // Canonical string representation with pipe delimiters, normalized to lower case where appropriate
        String canonical = String.join("|",
                safeString(name).trim(),
                safeString(email).trim().toLowerCase(),
                safeString(phone).trim(),
                safeString(managerId).trim(),
                safeString(officeCode).trim().toUpperCase(),
                String.valueOf(active)
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String safeString(String val) {
        return val == null ? "" : val;
    }
}
