package com.rosterguard.api.controller;

import com.rosterguard.domain.employee.Employee;
import com.rosterguard.infrastructure.persistence.EmployeeRepository;
import com.rosterguard.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/employees")
@Tag(name = "Employee Query", description = "Tenant-isolated employee profile lookup")
public class EmployeeController {

    private final EmployeeRepository employeeRepository;

    public EmployeeController(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @GetMapping("/{employeeId}")
    @Operation(summary = "Get employee by ID within authenticated tenant")
    public ResponseEntity<Employee> getEmployeeById(@PathVariable String employeeId) {
        String tenantId = TenantContext.getTenantId();
        return employeeRepository.findById(tenantId, employeeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
