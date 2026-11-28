package za.co.salon.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupplierRequest {

    @NotBlank
    private String name;

    private String contactPerson;

    private String email;

    private String phoneNumber;

    private String address;
}
