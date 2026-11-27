package za.co.salon.catalogue.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommissionRuleResponse {

    private Long id;
    private Long employeeId;
    private BigDecimal percentageRate;
    private boolean active;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

}
