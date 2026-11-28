package za.co.salon.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PurchaseItemRequest {

    @NotNull
    private Long productId;

    @NotNull
    @Positive
    private BigDecimal quantity;

    @NotNull
    @PositiveOrZero
    private BigDecimal unitCost;

    private BigDecimal taxAmount = BigDecimal.ZERO;
}
