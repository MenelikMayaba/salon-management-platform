package za.co.salon.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DraftSaleRequest {

    private Long customerId;

    @NotEmpty
    @Valid
    private List<SaleItemRequest> items;
}
