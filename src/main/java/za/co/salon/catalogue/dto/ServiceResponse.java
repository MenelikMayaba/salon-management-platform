package za.co.salon.catalogue.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceResponse {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String description;
    private int durationMinutes;
    private BigDecimal price;
    private Long taxCodeId;
    private boolean active;

}
