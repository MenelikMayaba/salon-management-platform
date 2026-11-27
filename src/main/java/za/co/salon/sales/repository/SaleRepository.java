package za.co.salon.sales.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.salon.sales.entity.Sale;

import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    Optional<Sale> findByIdAndBusinessId(Long id, Long businessId);

    Page<Sale> findAllByBusinessId(Long businessId, Pageable pageable);
}
