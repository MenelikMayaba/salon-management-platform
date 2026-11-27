package za.co.salon.finance.accounting;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.salon.finance.entity.SequenceCounter;
import za.co.salon.finance.repository.SequenceCounterRepository;

/**
 * Allocates gapless per-business sequence numbers.
 *
 * CRITICAL: propagation is REQUIRED - this method must join the caller's
 * transaction. REQUIRES_NEW is banned here on purpose: the increment commits
 * with the posting transaction, so if the posting rolls back the number returns
 * to the pool. A separate transaction would commit the increment even when the
 * posting aborts, burning numbers and breaking gaplessness (blueprint 3.8).
 */
@Service
public class SequenceNumberService {

    private final SequenceCounterRepository sequenceCounterRepository;

    public SequenceNumberService(SequenceCounterRepository sequenceCounterRepository) {
        this.sequenceCounterRepository = sequenceCounterRepository;
    }

    /**
     * Consumes the next value for the sequence. The counter row is locked
     * FOR UPDATE, so concurrent callers in the same business serialize here:
     * the second caller blocks until the first transaction commits or rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public long next(Long businessId, String sequenceName) {
        SequenceCounter counter = sequenceCounterRepository.findForUpdate(businessId, sequenceName)
                .orElseGet(() -> insertCounterRow(businessId, sequenceName));
        long consumed = counter.getNextValue();
        counter.setNextValue(consumed + 1);
        sequenceCounterRepository.save(counter);
        return consumed;
    }

    /**
     * Rows are normally seeded at business onboarding, so this is the
     * defense-in-depth path for unseeded sequences. Two first-time callers can
     * race the insert: the loser hits the unique key and re-reads the winner's row.
     */
    private SequenceCounter insertCounterRow(Long businessId, String sequenceName) {
        try {
            SequenceCounter counter = new SequenceCounter();
            counter.setBusinessId(businessId);
            counter.setSequenceName(sequenceName);
            counter.setNextValue(1L);
            return sequenceCounterRepository.saveAndFlush(counter);
        } catch (DataIntegrityViolationException duplicateKey) {
            return sequenceCounterRepository.findForUpdate(businessId, sequenceName)
                    .orElseThrow(() -> duplicateKey);
        }
    }
}
