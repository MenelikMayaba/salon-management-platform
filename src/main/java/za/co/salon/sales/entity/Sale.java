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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.salon.shared.security.BusinessScoped;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Blueprint 3.5: a combined checkout. sale_number is allocated only at completion
 * from sequence_counters, so DRAFT rows carry null - the contract's NOT NULL
 * applies to completed sales, and MySQL treats NULLs as distinct in the unique
 * index, so drafts never collide. Documented deviation from the literal contract.
 */
@Getter
@Setter
@Entity
@Table(name = "sales", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"business_id", "sale_number"})
})
public class Sale implements BusinessScoped {

    public enum Status {
        DRAFT, COMPLETED, VOIDED, PARTIALLY_REFUNDED, REFUNDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long businessId;

    private Long customerId;

    @Column(length = 50)
    private String saleNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 25)
    private Status status = Status.DRAFT;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    private Long createdBy;

    private Instant completedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
