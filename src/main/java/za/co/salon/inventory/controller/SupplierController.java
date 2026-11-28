package za.co.salon.inventory.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import za.co.salon.inventory.dto.SupplierRequest;
import za.co.salon.inventory.dto.SupplierResponse;
import za.co.salon.inventory.service.SupplierService;

/**
 * Suppliers are back-office data - OWNER/MANAGER only per the 7.4 matrix.
 */
@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public SupplierResponse create(@Valid @RequestBody SupplierRequest request) {
        return supplierService.create(request);
    }

    @GetMapping
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public Page<SupplierResponse> list(Pageable pageable) {
        return supplierService.list(pageable);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public SupplierResponse update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return supplierService.update(id, request);
    }
}
