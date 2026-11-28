package za.co.salon.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class PurchaseRequest {

    @NotNull
    private Long supplierId;

    private String invoiceNumber;

    @NotNull
    private LocalDate purchaseDate;

    @NotEmpty
    @Valid
    private List<PurchaseItemRequest> items;
}
