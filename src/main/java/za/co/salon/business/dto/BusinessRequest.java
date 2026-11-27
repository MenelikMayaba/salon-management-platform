package za.co.salon.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BusinessRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 100)
    private String registrationNumber;

    @Size(max = 255)
    private String email;

    @Size(max = 30)
    private String phoneNumber;

    @NotBlank
    @Size(max = 255)
    private String addressLine1;

    @Size(max = 255)
    private String addressLine2;

    @NotBlank
    @Size(max = 100)
    private String city;

    @NotBlank
    @Size(max = 100)
    private String province;

    @Size(max = 20)
    private String postalCode;

}
