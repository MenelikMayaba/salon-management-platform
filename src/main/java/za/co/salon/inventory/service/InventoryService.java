package za.co.salon.inventory.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.salon.inventory.entity.InventoryMovement;
import za.co.salon.inventory.entity.ProductStockBalance;
import za.co.salon.inventory.repository.InventoryMovementRepository;
import za.co.salon.inventory.repository.ProductStockBalanceRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stock movements. Sale deduction runs inside the caller's transaction (the
 * sale completion): balances are locked FOR UPDATE in sorted product-id order
 * FIRST and validated in full BEFORE anything is written - a validation failure
 * must leave the transaction untouched, not merely rolled back after the fact.
 */
@Service
public class InventoryService {

    private final ProductStockBalanceRepository balanceRepository;
    private final InventoryMovementRepository movementRepository;

    public InventoryService(ProductStockBalanceRepository balanceRepository,
                            InventoryMovementRepository movementRepository) {
        this.balanceRepository = balanceRepository;
        this.movementRepository = movementRepository;
    }

    /**
     * Deducts sold quantities and writes movement audit rows. quantitiesByProductId
     * must already be sorted by product id - deterministic lock order is the
     * deadlock mitigation for concurrent completions.
     *
     * @return cost per product (qty x average unit cost) for the COGS posting
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Map<Long, BigDecimal> recordSaleMovements(Long businessId, Long saleId,
                                                     Map<Long, BigDecimal> quantitiesByProductId,
                                                     Long createdByUserId) {
        // Pass 1: lock every affected balance in sorted order and validate in full.
        Map<Long, ProductStockBalance> balances = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> entry : quantitiesByProductId.entrySet()) {
            Long productId = entry.getKey();
            BigDecimal required = entry.getValue();
            Optional<ProductStockBalance> locked = balanceRepository.findForUpdate(businessId, productId);
            BigDecimal onHand = locked.map(ProductStockBalance::getQuantityOnHand).orElse(BigDecimal.ZERO);
            if (onHand.compareTo(required) < 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Insufficient stock for product " + productId
                                + ": required " + required + ", on hand " + onHand);
            }
            if (locked.isPresent()) {
                balances.put(productId, locked.get());
            }
        }

        // Pass 2: apply deductions and write the audit trail.
        Map<Long, BigDecimal> costByProduct = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> entry : quantitiesByProductId.entrySet()) {
            Long productId = entry.getKey();
            BigDecimal quantity = entry.getValue();
            ProductStockBalance balance = balances.get(productId);
            BigDecimal unitCost = balance == null ? BigDecimal.ZERO : balance.getAverageUnitCost();

            if (balance != null) {
                balance.setQuantityOnHand(balance.getQuantityOnHand().subtract(quantity));
                balanceRepository.save(balance);
            }

            InventoryMovement movement = new InventoryMovement();
            movement.setBusinessId(businessId);
            movement.setProductId(productId);
            movement.setMovementType(InventoryMovement.MovementType.SALE);
            movement.setQuantityChange(quantity.negate());
            movement.setUnitCost(unitCost);
            movement.setReferenceType("SALE");
            movement.setReferenceId(saleId);
            movement.setCreatedBy(createdByUserId);
            movementRepository.save(movement);

            costByProduct.put(productId, quantity.multiply(unitCost).setScale(4, RoundingMode.HALF_UP));
        }
        return costByProduct;
    }
}
