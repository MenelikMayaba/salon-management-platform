package za.co.salon.inventory.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupplierResponse {

    private Long id;
    private String name;
    private String contactPerson;
    private String email;
    private String phoneNumber;
    private String address;
}
