package za.co.salon.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentRequest {

    @NotBlank
    private String paymentMethod;

    @NotNull
    @Positive
    private BigDecimal amount;

    private String referenceNumber;

    @NotBlank
    private String idempotencyKey;
}
