package za.co.salon.catalogue.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommissionRuleRequest {

    @NotNull
    @PositiveOrZero
    private BigDecimal percentageRate;

    @NotNull
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private boolean active = true;

}
