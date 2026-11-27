package za.co.salon.finance.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.salon.finance.entity.SequenceCounter;
import za.co.salon.finance.entity.SequenceCounterId;

import java.util.Optional;

public interface SequenceCounterRepository extends JpaRepository<SequenceCounter, SequenceCounterId> {

    Optional<SequenceCounter> findByBusinessIdAndSequenceName(Long businessId, String sequenceName);

    /**
     * The JPA spelling of SELECT ... FOR UPDATE: serializes concurrent number
     * allocation on the counter row inside the caller's transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM SequenceCounter c WHERE c.businessId = :businessId AND c.sequenceName = :sequenceName")
    Optional<SequenceCounter> findForUpdate(@Param("businessId") Long businessId,
                                            @Param("sequenceName") String sequenceName);
}
