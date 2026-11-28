package za.co.salon.inventory.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import za.co.salon.inventory.dto.PayPurchaseRequest;
import za.co.salon.inventory.dto.PurchaseRequest;
import za.co.salon.inventory.dto.PurchaseResponse;
import za.co.salon.inventory.service.PurchaseService;

/**
 * Purchases are back-office stock-in workflow - OWNER/MANAGER only per 7.4.
 */
@RestController
@RequestMapping("/api/v1/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public PurchaseResponse create(@Valid @RequestBody PurchaseRequest request) {
        return purchaseService.createDraft(request);
    }

    @GetMapping
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public Page<PurchaseResponse> list(Pageable pageable) {
        return purchaseService.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public PurchaseResponse getById(@PathVariable Long id) {
        return purchaseService.getById(id);
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public PurchaseResponse receive(@PathVariable Long id) {
        return purchaseService.receive(id);
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public PurchaseResponse pay(@PathVariable Long id, @Valid @RequestBody PayPurchaseRequest request) {
        return purchaseService.pay(id, request);
    }
}
