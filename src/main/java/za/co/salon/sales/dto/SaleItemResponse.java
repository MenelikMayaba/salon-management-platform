package za.co.salon.sales.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SaleItemResponse {

    private Long id;
    private String itemType;
    private Long serviceId;
    private Long productId;
    private Long employeeId;
    private String descriptionSnapshot;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal lineTotal;
}
