package za.co.salon.inventory.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PurchaseItemResponse {

    private Long id;
    private Long productId;
    private String productName;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal taxAmount;
    private BigDecimal lineTotal;
}
