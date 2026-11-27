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
 * Blueprint 3.5: refunds link to the original sale and generate accounting
 * reversals - never silent money movement.
 */
@Getter
@Setter
@Entity
@Table(name = "refunds")
public class Refund implements BusinessScoped {

    public enum Status {
        COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long businessId;

    @Column(nullable = false)
    private Long saleId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status = Status.COMPLETED;

    private Long refundedBy;

    private Instant refundedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
