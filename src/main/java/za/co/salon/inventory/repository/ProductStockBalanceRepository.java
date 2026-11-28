package za.co.salon.inventory.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.salon.inventory.entity.ProductStockBalance;

import java.util.Optional;

public interface ProductStockBalanceRepository extends JpaRepository<ProductStockBalance, Long> {

    Optional<ProductStockBalance> findByBusinessIdAndProductId(Long businessId, Long productId);

    java.util.List<ProductStockBalance> findAllByBusinessId(Long businessId);

    /**
     * SELECT ... FOR UPDATE on the balance row - the serialization point that
     * makes concurrent stock movements correct (blueprint 3.6).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ProductStockBalance b WHERE b.businessId = :businessId AND b.productId = :productId")
    Optional<ProductStockBalance> findForUpdate(@Param("businessId") Long businessId,
                                                @Param("productId") Long productId);
}
