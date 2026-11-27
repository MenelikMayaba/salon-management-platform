package za.co.salon.catalogue.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductResponse {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String sku;
    private String barcode;
    private BigDecimal sellingPrice;
    private BigDecimal reorderLevel;
    private boolean trackInventory;
    private Long taxCodeId;
    private boolean active;

}
