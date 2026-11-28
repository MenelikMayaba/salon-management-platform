package za.co.salon.inventory.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class MovementResponse {

    private Long id;
    private Long productId;
    private String movementType;
    private BigDecimal quantityChange;
    private BigDecimal unitCost;
    private String referenceType;
    private Long referenceId;
    private String note;
    private Instant createdAt;
}
