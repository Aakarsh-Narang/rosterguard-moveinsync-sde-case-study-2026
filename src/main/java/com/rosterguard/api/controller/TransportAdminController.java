package com.rosterguard.api.controller;

import com.rosterguard.api.dto.TransportProfileUpdateRequest;
import com.rosterguard.domain.employee.EmployeeTransportProfile;
import com.rosterguard.infrastructure.persistence.EmployeeTransportProfileRepository;
import com.rosterguard.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/transport/employees")
@Tag(name = "Transport Administration", description = "Management of Transport-Admin owned preferences")
public class TransportAdminController {

    private final EmployeeTransportProfileRepository profileRepository;

    public TransportAdminController(EmployeeTransportProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @PutMapping("/{employeeId}")
    @Operation(summary = "Update transport preferences (pickup point, special assistance) for an employee")
    public ResponseEntity<EmployeeTransportProfile> updateTransportProfile(
            @PathVariable String employeeId,
            @Valid @RequestBody TransportProfileUpdateRequest request) {

        String tenantId = TenantContext.getTenantId();
        EmployeeTransportProfile profile = new EmployeeTransportProfile(
                tenantId, employeeId, request.getPreferredPickupPoint(),
                request.getSpecialAssistanceNeeds(), Instant.now()
        );

        profileRepository.upsert(profile);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/{employeeId}")
    @Operation(summary = "Get transport preferences for an employee")
    public ResponseEntity<EmployeeTransportProfile> getTransportProfile(@PathVariable String employeeId) {
        String tenantId = TenantContext.getTenantId();
        return profileRepository.findById(tenantId, employeeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
