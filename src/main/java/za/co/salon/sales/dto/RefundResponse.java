package za.co.salon.sales.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class RefundResponse {

    private final Long id;
    private final Long saleId;
    private final BigDecimal amount;
    private final String reason;
    private final String refundStatus;
    private final String saleStatus;

    public RefundResponse(Long id, Long saleId, BigDecimal amount, String reason,
                          String refundStatus, String saleStatus) {
        this.id = id;
        this.saleId = saleId;
        this.amount = amount;
        this.reason = reason;
        this.refundStatus = refundStatus;
        this.saleStatus = saleStatus;
    }
}
