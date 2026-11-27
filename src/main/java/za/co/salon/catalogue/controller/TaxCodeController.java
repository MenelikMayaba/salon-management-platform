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
import za.co.salon.catalogue.dto.TaxCodeRequest;
import za.co.salon.catalogue.dto.TaxCodeResponse;
import za.co.salon.catalogue.service.TaxCodeService;

/**
 * Tax code CRUD (blueprint 3.9). Gated to OWNER/MANAGER per the 7.4 matrix -
 * catalogue management is not a receptionist function.
 */
@RestController
@RequestMapping("/api/v1/tax-codes")
public class TaxCodeController {

    private final TaxCodeService taxCodeService;

    public TaxCodeController(TaxCodeService taxCodeService) {
        this.taxCodeService = taxCodeService;
    }

    @PostMapping
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public ResponseEntity<TaxCodeResponse> create(@Valid @RequestBody TaxCodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taxCodeService.create(request));
    }

    @GetMapping
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public Page<TaxCodeResponse> list(Pageable pageable) {
        return taxCodeService.list(pageable);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public TaxCodeResponse update(@PathVariable Long id, @Valid @RequestBody TaxCodeRequest request) {
        return taxCodeService.update(id, request);
    }
}
