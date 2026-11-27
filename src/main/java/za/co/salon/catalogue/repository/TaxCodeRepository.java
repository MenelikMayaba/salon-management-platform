package za.co.salon.catalogue.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.salon.catalogue.entity.TaxCode;

import java.util.Optional;

public interface TaxCodeRepository extends JpaRepository<TaxCode, Long> {

    Optional<TaxCode> findByIdAndBusinessId(Long id, Long businessId);

    Page<TaxCode> findAllByBusinessId(Long businessId, Pageable pageable);
}
