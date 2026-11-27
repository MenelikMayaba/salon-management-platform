package za.co.salon.catalogue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductCategoryRequest {

    @NotBlank
    private String name;

    private boolean active = true;

}
