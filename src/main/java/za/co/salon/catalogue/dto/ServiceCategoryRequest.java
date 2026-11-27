package za.co.salon.catalogue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceCategoryRequest {

    @NotBlank
    private String name;

    private String description;

    private boolean active = true;

}
