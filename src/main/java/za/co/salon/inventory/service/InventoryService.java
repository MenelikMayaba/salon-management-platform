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
     * Manual adjustment: found stock, damage, opening corrections. The average
     * cost never changes - stock moves at the current average. Negative results
     * are rejected: the REJECT negative-stock policy is the default (section 12).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordAdjustment(Long businessId, Long productId, BigDecimal quantityChange,
                                 InventoryMovement.MovementType movementType, String reason, Long createdByUserId) {
        if (movementType != InventoryMovement.MovementType.STOCK_ADJUSTMENT
                && movementType != InventoryMovement.MovementType.DAMAGE) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Adjustments must be STOCK_ADJUSTMENT or DAMAGE, not " + movementType);
        }
        ProductStockBalance balance = balanceRepository.findForUpdate(businessId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No stock balance row for product " + productId));
        BigDecimal newQuantity = balance.getQuantityOnHand().add(quantityChange);
        if (newQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Adjustment would drive stock negative: on hand " + balance.getQuantityOnHand()
                            + ", change " + quantityChange);
        }
        balance.setQuantityOnHand(newQuantity);
        balanceRepository.save(balance);

        InventoryMovement movement = new InventoryMovement();
        movement.setBusinessId(businessId);
        movement.setProductId(productId);
        movement.setMovementType(movementType);
        movement.setQuantityChange(quantityChange);
        movement.setUnitCost(balance.getAverageUnitCost());
        movement.setReferenceType("ADJUSTMENT");
        movement.setNote(reason);
        movement.setCreatedBy(createdByUserId);
        movementRepository.save(movement);
    }

    /**
     * Blueprint 3.6/8.2 weighted-average-cost receipt: lock the balance row
     * FOR UPDATE, then newAvg = (qty x avg + received x unitCost) / newQty.
     * The lock is the feature - without it two concurrent receipts calculate
     * from the same old balance and one update is silently lost. Division is
     * explicit scale-4 HALF_UP because non-terminating decimals throw.
     *
     * The balance row must exist (created at product creation); a missing row
     * means data predating that invariant, and we reject loudly instead of
     * locking a gap.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordReceipt(Long businessId, za.co.salon.inventory.entity.PurchaseItem item, Long createdByUserId) {
        ProductStockBalance balance = balanceRepository.findForUpdate(businessId, item.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No stock balance row for product " + item.getProductId()
                                + " - product predates the create-at-creation invariant"));

        BigDecimal oldQuantity = balance.getQuantityOnHand();
        BigDecimal received = item.getQuantity();
        BigDecimal newQuantity = oldQuantity.add(received);
        BigDecimal newAverage;
        if (newQuantity.compareTo(BigDecimal.ZERO) == 0) {
            newAverage = BigDecimal.ZERO;
        } else {
            BigDecimal oldValue = oldQuantity.multiply(balance.getAverageUnitCost());
            BigDecimal receivedValue = received.multiply(item.getUnitCost());
            newAverage = oldValue.add(receivedValue).divide(newQuantity, 4, RoundingMode.HALF_UP);
        }
        balance.setQuantityOnHand(newQuantity);
        balance.setAverageUnitCost(newAverage);
        balanceRepository.save(balance);

        InventoryMovement movement = new InventoryMovement();
        movement.setBusinessId(businessId);
        movement.setProductId(item.getProductId());
        movement.setMovementType(InventoryMovement.MovementType.PURCHASE_RECEIPT);
        movement.setQuantityChange(received);
        movement.setUnitCost(item.getUnitCost());
        movement.setReferenceType("PURCHASE");
        movement.setReferenceId(item.getPurchaseId());
        movement.setCreatedBy(createdByUserId);
        movementRepository.save(movement);
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
