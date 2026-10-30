package com.rosterguard.unit;

import com.rosterguard.domain.employee.CanonicalHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalHasherTest {

    @Test
    @DisplayName("CanonicalHasher produces deterministic hash")
    void deterministicHash() {
        String hash1 = CanonicalHasher.computeHash("Alice", "alice@example.com", "+919876543210", "MGR-1", "BLR-01", true);
        String hash2 = CanonicalHasher.computeHash("Alice", "alice@example.com", "+919876543210", "MGR-1", "BLR-01", true);
        assertEquals(hash1, hash2);
    }

    @Test
    @DisplayName("CanonicalHasher normalizes casing and whitespace")
    void normalizesCasingAndWhitespace() {
        String hash1 = CanonicalHasher.computeHash("Alice", "alice@example.com", "+919876543210", "MGR-1", "blr-01", true);
        String hash2 = CanonicalHasher.computeHash("Alice", "ALICE@EXAMPLE.COM ", "+919876543210", "MGR-1", "BLR-01", true);
        assertEquals(hash1, hash2);
    }

    @Test
    @DisplayName("Change in any HR field alters the hash")
    void fieldChangeAltersHash() {
        String hash1 = CanonicalHasher.computeHash("Alice", "alice@example.com", "+919876543210", "MGR-1", "BLR-01", true);
        String hash2 = CanonicalHasher.computeHash("Alice", "alice@example.com", "+919876543210", "MGR-1", "HYD-01", true);
        assertNotEquals(hash1, hash2);
    }
}
