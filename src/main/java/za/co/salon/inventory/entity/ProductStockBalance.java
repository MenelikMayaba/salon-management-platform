package za.co.salon.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import za.co.salon.shared.security.BusinessScoped;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Blueprint 3.6: current stock quantity and weighted average cost per product.
 * Rows are locked FOR UPDATE inside movement transactions - two simultaneous
 * movements must never calculate from the same stale balance.
 */
@Getter
@Setter
@Entity
@Table(name = "product_stock_balances")
public class ProductStockBalance implements BusinessScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long businessId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal quantityOnHand = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal averageUnitCost = BigDecimal.ZERO;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
