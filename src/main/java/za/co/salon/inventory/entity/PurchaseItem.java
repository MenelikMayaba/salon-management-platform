package za.co.salon.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import za.co.salon.shared.security.BusinessScoped;

import java.math.BigDecimal;

/**
 * Blueprint 3.7: one line of a purchase - product, quantity and the cost the
 * supplier charged, which becomes the receipt's weighted-average-cost input.
 */
@Getter
@Setter
@Entity
@Table(name = "purchase_items")
public class PurchaseItem implements BusinessScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long businessId;

    @Column(nullable = false)
    private Long purchaseId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotal;
}
