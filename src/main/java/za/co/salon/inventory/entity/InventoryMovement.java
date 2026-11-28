package za.co.salon.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.salon.shared.security.BusinessScoped;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Blueprint 3.6: the immutable audit trail of every stock change. Balances are
 * derived state; movements are the truth. Sale deductions are negative changes.
 */
@Getter
@Setter
@Entity
@Table(name = "inventory_movements")
public class InventoryMovement implements BusinessScoped {

    public enum MovementType {
        SALE, PURCHASE_RECEIPT, CUSTOMER_RETURN, STOCK_ADJUSTMENT, DAMAGE, OPENING_BALANCE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long businessId;

    @Column(nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private MovementType movementType;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal quantityChange;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(length = 30)
    private String referenceType;

    private Long referenceId;

    @Column(length = 500)
    private String note;

    private Long createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
