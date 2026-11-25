package za.co.salon.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class AddMemberRequest {

    @NotBlank
    private String email;

    @NotBlank
    @Pattern(regexp = "OWNER|MANAGER|RECEPTIONIST|STYLIST|ACCOUNTANT")
    private String role;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
