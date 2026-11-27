package za.co.salon.sales.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.salon.sales.dto.DraftSaleRequest;
import za.co.salon.sales.dto.PaymentRequest;
import za.co.salon.sales.dto.PaymentResponse;
import za.co.salon.sales.dto.SaleResponse;
import za.co.salon.sales.service.SaleService;

/**
 * Combined checkout. Creation and completion include RECEPTIONIST and STYLIST
 * per the 7.4 matrix - front-desk and stylists both ring up sales.
 */
@RestController
@RequestMapping("/api/v1/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @PostMapping
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER','RECEPTIONIST','STYLIST')")
    public ResponseEntity<SaleResponse> createDraft(@Valid @RequestBody DraftSaleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(saleService.createDraft(request));
    }

    @GetMapping
    @PreAuthorize("@authz.requireMembership()")
    public Page<SaleResponse> list(Pageable pageable) {
        return saleService.list(pageable);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER','RECEPTIONIST','STYLIST')")
    public SaleResponse complete(@PathVariable Long id) {
        return saleService.complete(id);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER','RECEPTIONIST','STYLIST')")
    public SaleResponse voidSale(@PathVariable Long id) {
        return saleService.voidSale(id);
    }

    @PostMapping("/{id}/refunds")
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER','RECEPTIONIST')")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public za.co.salon.sales.dto.RefundResponse refund(@PathVariable Long id,
                                                       @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody za.co.salon.sales.dto.RefundRequest request) {
        return saleService.refund(id, request);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER','RECEPTIONIST','STYLIST')")
    public ResponseEntity<PaymentResponse> addPayment(@PathVariable Long id,
                                                      @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(saleService.addPayment(id, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.requireMembership()")
    public SaleResponse getById(@PathVariable Long id) {
        return saleService.getById(id);
    }
}
