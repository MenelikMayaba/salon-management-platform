package za.co.salon.inventory.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class InventoryBalanceResponse {

    private Long productId;
    private String productName;
    private BigDecimal quantityOnHand;
    private BigDecimal averageUnitCost;
    private BigDecimal reorderLevel;
    private boolean lowStock;
}
