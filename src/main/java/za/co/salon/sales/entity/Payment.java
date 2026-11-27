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
 * Blueprint 3.5: one payment against a sale; split payments are multiple rows.
 * The idempotency key is the double-charge guard: a retried request hits the
 * unique constraint and returns the original payment instead.
 */
@Getter
@Setter
@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"business_id", "idempotency_key"})
})
public class Payment implements BusinessScoped {

    public enum Method {
        CASH, CARD, EFT, OTHER
    }

    public enum Status {
        PENDING, SUCCESS, FAILED
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
    private Method paymentMethod;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status = Status.SUCCESS;

    @Column(length = 150)
    private String referenceNumber;

    @Column(nullable = false, length = 150)
    private String idempotencyKey;

    private Instant receivedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
