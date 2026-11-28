package za.co.salon.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.salon.inventory.entity.Purchase;

import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    Page<Purchase> findAllByBusinessId(Long businessId, Pageable pageable);

    Optional<Purchase> findByIdAndBusinessId(Long id, Long businessId);
}
