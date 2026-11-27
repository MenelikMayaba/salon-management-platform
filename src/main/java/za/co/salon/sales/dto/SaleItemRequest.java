package za.co.salon.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SaleItemRequest {

    @NotBlank
    private String itemType;

    private Long serviceId;

    private Long productId;

    private Long employeeId;

    @NotNull
    @Positive
    private BigDecimal quantity;

    private BigDecimal discountAmount = BigDecimal.ZERO;
}
