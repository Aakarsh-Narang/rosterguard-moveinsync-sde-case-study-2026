package com.rosterguard.config;

import com.rosterguard.infrastructure.persistence.OfficeRepository;
import com.rosterguard.infrastructure.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final UserRepository userRepository;
    private final OfficeRepository officeRepository;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public DatabaseInitializer(UserRepository userRepository,
                               OfficeRepository officeRepository,
                               PasswordEncoder passwordEncoder,
                               @Value("${roster.security.demo-password:${ROSTER_DEMO_PASSWORD:}}") String demoPassword) {
        this.userRepository = userRepository;
        this.officeRepository = officeRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(String... args) {
        // Ensure tenants exist before seeding referencing offices
        userRepository.insertTenantIfNotExists("tenant-moveinsync", "MoveInSync Corporate");
        userRepository.insertTenantIfNotExists("tenant-acme", "Acme Corporation");

        // Seed offices (reference data)
        officeRepository.insert("tenant-moveinsync", "BLR-01", "Bengaluru Tech Park HQ");
        officeRepository.insert("tenant-moveinsync", "HYD-01", "Hyderabad Hitech City");
        officeRepository.insert("tenant-moveinsync", "DEL-01", "Gurugram Cyber Hub");

        officeRepository.insert("tenant-acme", "BLR-01", "Acme Bangalore Center");
        officeRepository.insert("tenant-acme", "MUM-01", "Acme Mumbai Financial Center");

        // Seed demo accounts only when ROSTER_DEMO_PASSWORD is explicitly configured
        if (demoPassword != null && !demoPassword.isBlank()) {
            log.info("ROSTER_DEMO_PASSWORD configured: seeding/updating demo tenant credentials");
            seedTenant("tenant-moveinsync", "MoveInSync Corporate");
            seedTenant("tenant-acme", "Acme Corporation");
        } else {
            log.info("ROSTER_DEMO_PASSWORD not provided: skipping automatic demo credential seeding");
        }
    }

    private void seedTenant(String tenantId, String tenantName) {
        String defaultPwHash = passwordEncoder.encode(demoPassword);
        userRepository.insertTenantAndUser(tenantId, tenantName, "hr_sync", defaultPwHash, "HR_SYNC");
        userRepository.insertTenantAndUser(tenantId, tenantName, "admin", defaultPwHash, "ADMIN");
        userRepository.insertTenantAndUser(tenantId, tenantName, "operator", defaultPwHash, "OPERATOR");
        userRepository.insertTenantAndUser(tenantId, tenantName, "employee", defaultPwHash, "EMPLOYEE");
    }
}
