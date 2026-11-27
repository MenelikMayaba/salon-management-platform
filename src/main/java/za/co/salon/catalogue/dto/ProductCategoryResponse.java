package za.co.salon.catalogue.dto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductCategoryResponse {

    private Long id;
    private String name;
    private boolean active;

}
