package za.co.salon.finance.entity;

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
 * Blueprint 3.8: one debit or credit line. The XOR shape (exactly one of
 * debit/credit positive) and the entry-level balance are enforced in the
 * posting service - auto-DDL emits no CHECK constraints (blueprint 2.4).
 */
@Getter
@Setter
@Entity
@Table(name = "journal_entry_lines")
public class JournalEntryLine implements BusinessScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long businessId;

    @Column(nullable = false)
    private Long journalEntryId;

    @Column(nullable = false)
    private Long accountId;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal credit = BigDecimal.ZERO;
}
