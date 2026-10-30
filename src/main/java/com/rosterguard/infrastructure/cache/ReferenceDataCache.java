package com.rosterguard.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.rosterguard.infrastructure.persistence.OfficeRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ReferenceDataCache {

    private final OfficeRepository officeRepository;
    // Bounded Caffeine cache: max 10,000 entries, expires 60 seconds after write
    private final Cache<String, Boolean> officeValidationCache;

    public ReferenceDataCache(OfficeRepository officeRepository) {
        this.officeRepository = officeRepository;
        this.officeValidationCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(60))
                .recordStats()
                .build();
    }

    public boolean isValidOffice(String tenantId, String officeCode) {
        if (tenantId == null || officeCode == null || officeCode.isBlank()) {
            return false;
        }
        String key = tenantId + ":" + officeCode.trim().toUpperCase();
        return officeValidationCache.get(key, k -> officeRepository.exists(tenantId, officeCode.trim().toUpperCase()));
    }

    public void invalidateOffice(String tenantId, String officeCode) {
        if (tenantId != null && officeCode != null) {
            officeValidationCache.invalidate(tenantId + ":" + officeCode.trim().toUpperCase());
        }
    }

    public void invalidateAll() {
        officeValidationCache.invalidateAll();
    }
}
