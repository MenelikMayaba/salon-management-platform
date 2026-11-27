package za.co.salon.finance.entity;

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

import java.time.Instant;
import java.time.LocalDate;

/**
 * Blueprint 3.8: a financial event in the general ledger. Posted entries are
 * immutable - corrections use reversal entries, never edits. The two unique
 * constraints give gapless per-business numbering and source-based idempotency.
 */
@Getter
@Setter
@Entity
@Table(name = "journal_entries", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"business_id", "entry_number"}),
        @UniqueConstraint(columnNames = {"business_id", "source_type", "source_id"})
})
public class JournalEntry implements BusinessScoped {

    public enum Status {
        DRAFT, POSTED, REVERSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long businessId;

    @Column(nullable = false)
    private Long entryNumber;

    @Column(nullable = false, length = 40)
    private String sourceType;

    private Long sourceId;

    @Column(nullable = false)
    private LocalDate postingDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status;

    private Long reversalOfId;

    private Long createdBy;

    private Instant postedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
