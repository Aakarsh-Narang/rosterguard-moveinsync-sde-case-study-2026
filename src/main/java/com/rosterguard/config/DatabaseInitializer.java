package com.rosterguard.config;

import com.rosterguard.infrastructure.persistence.OfficeRepository;
import com.rosterguard.infrastructure.persistence.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final OfficeRepository officeRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseInitializer(UserRepository userRepository,
                               OfficeRepository officeRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.officeRepository = officeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Seed default tenants and users
        seedTenant("tenant-moveinsync", "MoveInSync Corporate");
        seedTenant("tenant-acme", "Acme Corporation");

        // Seed offices
        officeRepository.insert("tenant-moveinsync", "BLR-01", "Bengaluru Tech Park HQ");
        officeRepository.insert("tenant-moveinsync", "HYD-01", "Hyderabad Hitech City");
        officeRepository.insert("tenant-moveinsync", "DEL-01", "Gurugram Cyber Hub");

        officeRepository.insert("tenant-acme", "BLR-01", "Acme Bangalore Center");
        officeRepository.insert("tenant-acme", "MUM-01", "Acme Mumbai Financial Center");
    }

    private void seedTenant(String tenantId, String tenantName) {
        String defaultPwHash = passwordEncoder.encode("secret123");
        userRepository.insertTenantAndUser(tenantId, tenantName, "hr_sync", defaultPwHash, "HR_SYNC");
        userRepository.insertTenantAndUser(tenantId, tenantName, "admin", defaultPwHash, "ADMIN");
        userRepository.insertTenantAndUser(tenantId, tenantName, "operator", defaultPwHash, "OPERATOR");
        userRepository.insertTenantAndUser(tenantId, tenantName, "employee", defaultPwHash, "EMPLOYEE");
    }
}
