package za.co.salon.sales.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class SaleResponse {

    private Long id;
    private Long customerId;
    private String saleNumber;
    private String status;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private Instant completedAt;
    private List<SaleItemResponse> items;
}
