package za.co.salon.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Blueprint 3.8: gapless per-business document numbering. Numbers are consumed
 * under a pessimistic row lock inside the caller's transaction, so a rollback
 * returns the number to the pool - no gaps from aborted postings.
 */
@Getter
@Setter
@Entity
@Table(name = "sequence_counters")
@IdClass(SequenceCounterId.class)
public class SequenceCounter {

    @Id
    private Long businessId;

    @Id
    @Column(length = 40)
    private String sequenceName;

    @Column(nullable = false)
    private long nextValue = 1L;
}
