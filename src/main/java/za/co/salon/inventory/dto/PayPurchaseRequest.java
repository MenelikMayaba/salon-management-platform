package za.co.salon.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PayPurchaseRequest {

    @NotNull
    @Positive
    private BigDecimal amount;

    /** CASH pays from 1000 Cash on Hand; anything else pays from 1010 Bank. */
    @NotNull
    private String paymentMethod;
}
