package za.co.salon.catalogue.dto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceCategoryResponse {

    private Long id;
    private String name;
    private String description;
    private boolean active;

}
