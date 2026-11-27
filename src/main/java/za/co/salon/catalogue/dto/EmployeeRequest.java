package za.co.salon.catalogue.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeeRequest {

    private Long userId;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    private String email;

    private String phoneNumber;

    @NotBlank
    private String employmentStatus;

    private LocalDate employmentStartDate;

}
