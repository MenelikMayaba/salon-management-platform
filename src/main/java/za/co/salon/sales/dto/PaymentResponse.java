package za.co.salon.sales.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class PaymentResponse {

    private Long id;
    private String paymentMethod;
    private BigDecimal amount;
    private String status;
    private String referenceNumber;
    private String idempotencyKey;
    private Instant receivedAt;
}
