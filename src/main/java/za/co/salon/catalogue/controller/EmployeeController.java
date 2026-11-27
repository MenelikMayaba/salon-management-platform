package za.co.salon.catalogue.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.salon.catalogue.dto.CommissionRuleRequest;
import za.co.salon.catalogue.dto.CommissionRuleResponse;
import za.co.salon.catalogue.dto.EmployeeRequest;
import za.co.salon.catalogue.dto.EmployeeResponse;
import za.co.salon.catalogue.service.EmployeeService;

/**
 * Employees and nested commission rules. Writes are OWNER/MANAGER per the
 * endpoint matrix; reads are open to any active member.
 */
@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.create(request));
    }

    @GetMapping
    @PreAuthorize("@authz.requireMembership()")
    public Page<EmployeeResponse> list(Pageable pageable) {
        return employeeService.list(pageable);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public EmployeeResponse update(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request) {
        return employeeService.update(id, request);
    }

    @PostMapping("/{id}/commission-rules")
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public ResponseEntity<CommissionRuleResponse> createCommissionRule(@PathVariable Long id,
                                                                       @Valid @RequestBody CommissionRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.createCommissionRule(id, request));
    }
}
