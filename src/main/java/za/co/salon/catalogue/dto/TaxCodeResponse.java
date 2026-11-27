package za.co.salon.catalogue.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaxCodeResponse {

    private Long id;
    private String name;
    private BigDecimal rate;
    private String taxType;
    private boolean active;

}
