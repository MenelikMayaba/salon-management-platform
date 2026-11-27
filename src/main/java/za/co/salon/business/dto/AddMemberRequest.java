package za.co.salon.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddMemberRequest {

    @NotBlank
    private String email;

    @NotBlank
    @Pattern(regexp = "OWNER|MANAGER|RECEPTIONIST|STYLIST|ACCOUNTANT")
    private String role;

}
