package za.co.salon.sales.entity;

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
 * Blueprint 3.5: one checkout line - service or product, never both. Description,
 * price, discount and tax are SNAPSHOTS copied at draft time; a later catalogue
 * price change must never alter an existing receipt.
 */
@Getter
@Setter
@Entity
@Table(name = "sale_items")
public class SaleItem implements BusinessScoped {

    public enum ItemType {
        SERVICE, PRODUCT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long businessId;

    @Column(nullable = false)
    private Long saleId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ItemType itemType;

    private Long serviceId;

    private Long productId;

    private Long employeeId;

    @Column(nullable = false, length = 255)
    private String descriptionSnapshot;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotal;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
