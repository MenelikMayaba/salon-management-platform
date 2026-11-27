package za.co.salon.catalogue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerRequest {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    private String email;

    private String phoneNumber;

    private String notes;

    private boolean active = true;

}
