package za.co.salon.business.dto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BusinessResponse {

    private Long id;
    private String name;
    private String email;
    private String phoneNumber;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String province;
    private String postalCode;
    private String countryCode;
    private String currencyCode;
    private String timezone;

}
