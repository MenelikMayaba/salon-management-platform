package za.co.salon.inventory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AdjustmentRequest {

    @NotNull
    private Long productId;

    /** Positive increases stock (STOCK_ADJUSTMENT), negative decreases (DAMAGE). */
    @NotNull
    private BigDecimal quantityChange;

    @NotNull
    private String movementType;

    private String reason;
}
