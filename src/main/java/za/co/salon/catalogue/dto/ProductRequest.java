package za.co.salon.catalogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductRequest {

    @NotNull
    private Long categoryId;

    @NotBlank
    private String name;

    private String sku;

    private String barcode;

    @NotNull
    @PositiveOrZero
    private BigDecimal sellingPrice;

    private BigDecimal reorderLevel = BigDecimal.ZERO;

    private boolean trackInventory = true;

    private Long taxCodeId;

    private boolean active = true;

}
