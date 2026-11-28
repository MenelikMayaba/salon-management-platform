package za.co.salon.inventory.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.salon.catalogue.entity.Product;
import za.co.salon.catalogue.repository.ProductRepository;
import za.co.salon.inventory.dto.InventoryBalanceResponse;
import za.co.salon.inventory.dto.MovementResponse;
import za.co.salon.inventory.entity.InventoryMovement;
import za.co.salon.inventory.entity.ProductStockBalance;
import za.co.salon.inventory.repository.InventoryMovementRepository;
import za.co.salon.inventory.repository.ProductStockBalanceRepository;
import za.co.salon.shared.security.BusinessContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inventory read paths. Product names and reorder levels are batch-fetched in
 * one query (blueprint rule 15) - never a repository call per row.
 */
@Service
public class InventoryQueryService {

    private final ProductStockBalanceRepository balanceRepository;
    private final InventoryMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final BusinessContext businessContext;

    public InventoryQueryService(ProductStockBalanceRepository balanceRepository,
                                 InventoryMovementRepository movementRepository,
                                 ProductRepository productRepository,
                                 BusinessContext businessContext) {
        this.balanceRepository = balanceRepository;
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
        this.businessContext = businessContext;
    }

    @Transactional(readOnly = true)
    public List<InventoryBalanceResponse> listBalances() {
        Long businessId = businessContext.getBusinessId();
        List<ProductStockBalance> balances = balanceRepository.findAllByBusinessId(businessId);

        List<Long> productIds = new ArrayList<>();
        for (ProductStockBalance balance : balances) {
            productIds.add(balance.getProductId());
        }
        Map<Long, Product> productsById = new HashMap<>();
        for (Product product : productRepository.findAllById(productIds)) {
            productsById.put(product.getId(), product);
        }

        List<InventoryBalanceResponse> responses = new ArrayList<>();
        for (ProductStockBalance balance : balances) {
            Product product = productsById.get(balance.getProductId());
            InventoryBalanceResponse response = new InventoryBalanceResponse();
            response.setProductId(balance.getProductId());
            response.setProductName(product == null ? null : product.getName());
            response.setQuantityOnHand(balance.getQuantityOnHand());
            response.setAverageUnitCost(balance.getAverageUnitCost());
            java.math.BigDecimal reorderLevel = product == null ? java.math.BigDecimal.ZERO : product.getReorderLevel();
            response.setReorderLevel(reorderLevel);
            // Blueprint: on_hand <= reorder_level. The reorderLevel > 0 guard keeps
            // products without a configured level out of the low-stock noise.
            response.setLowStock(reorderLevel.compareTo(java.math.BigDecimal.ZERO) > 0
                    && balance.getQuantityOnHand().compareTo(reorderLevel) <= 0);
            responses.add(response);
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public List<MovementResponse> movementsForProduct(Long productId) {
        Long businessId = businessContext.getBusinessId();
        productRepository.findByIdAndBusinessId(productId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        List<InventoryMovement> movements = movementRepository
                .findAllByBusinessIdAndProductIdOrderByIdDesc(businessId, productId);
        List<MovementResponse> responses = new ArrayList<>();
        for (InventoryMovement movement : movements) {
            MovementResponse response = new MovementResponse();
            response.setId(movement.getId());
            response.setProductId(movement.getProductId());
            response.setMovementType(movement.getMovementType().name());
            response.setQuantityChange(movement.getQuantityChange());
            response.setUnitCost(movement.getUnitCost());
            response.setReferenceType(movement.getReferenceType());
            response.setReferenceId(movement.getReferenceId());
            response.setNote(movement.getNote());
            response.setCreatedAt(movement.getCreatedAt());
            responses.add(response);
        }
        return responses;
    }
}
