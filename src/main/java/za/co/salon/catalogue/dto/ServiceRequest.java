package za.co.salon.catalogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceRequest {

    @NotNull
    private Long categoryId;

    @NotBlank
    private String name;

    private String description;

    @Positive
    private int durationMinutes;

    @NotNull
    @PositiveOrZero
    private BigDecimal price;

    private Long taxCodeId;

    private boolean active = true;

}
