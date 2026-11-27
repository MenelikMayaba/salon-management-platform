package za.co.salon.finance.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for sequence_counters: (business_id, sequence_name).
 * No surrogate id on this table per the blueprint contract.
 */
public class SequenceCounterId implements Serializable {

    private Long businessId;
    private String sequenceName;

    public SequenceCounterId() {
    }

    public SequenceCounterId(Long businessId, String sequenceName) {
        this.businessId = businessId;
        this.sequenceName = sequenceName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SequenceCounterId that)) {
            return false;
        }
        return Objects.equals(businessId, that.businessId)
                && Objects.equals(sequenceName, that.sequenceName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(businessId, sequenceName);
    }
}
