package za.co.salon.inventory.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import za.co.salon.inventory.dto.AdjustmentRequest;
import za.co.salon.inventory.dto.InventoryBalanceResponse;
import za.co.salon.inventory.dto.MovementResponse;
import za.co.salon.inventory.entity.InventoryMovement;
import za.co.salon.inventory.service.InventoryQueryService;
import za.co.salon.inventory.service.InventoryService;
import za.co.salon.shared.security.BusinessContext;
import za.co.salon.shared.security.SecurityUtils;

import java.util.List;

/**
 * Inventory reads are staff-wide (the front desk needs stock levels); manual
 * adjustments are back-office - OWNER/MANAGER only per 7.4.
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryQueryService queryService;
    private final BusinessContext businessContext;

    public InventoryController(InventoryService inventoryService,
                               InventoryQueryService queryService,
                               BusinessContext businessContext) {
        this.inventoryService = inventoryService;
        this.queryService = queryService;
        this.businessContext = businessContext;
    }

    @GetMapping
    @PreAuthorize("@authz.requireMembership()")
    public List<InventoryBalanceResponse> list() {
        return queryService.listBalances();
    }

    @GetMapping("/{productId}/movements")
    @PreAuthorize("@authz.requireMembership()")
    public List<MovementResponse> movements(@PathVariable Long productId) {
        return queryService.movementsForProduct(productId);
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public void adjust(@Valid @RequestBody AdjustmentRequest request) {
        inventoryService.recordAdjustment(businessContext.getBusinessId(), request.getProductId(),
                request.getQuantityChange(), InventoryMovement.MovementType.valueOf(request.getMovementType()),
                request.getReason(), SecurityUtils.currentUserId());
    }
}
