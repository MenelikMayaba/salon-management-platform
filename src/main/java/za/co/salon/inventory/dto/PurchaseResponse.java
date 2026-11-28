package za.co.salon.inventory.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class PurchaseResponse {

    private Long id;
    private Long supplierId;
    private String supplierName;
    private String invoiceNumber;
    private String status;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private LocalDate purchaseDate;
    private List<PurchaseItemResponse> items;
}
