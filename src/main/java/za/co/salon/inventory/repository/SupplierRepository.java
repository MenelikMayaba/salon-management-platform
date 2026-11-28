package za.co.salon.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.salon.inventory.entity.Supplier;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Page<Supplier> findAllByBusinessId(Long businessId, Pageable pageable);

    Optional<Supplier> findByIdAndBusinessId(Long id, Long businessId);
}
