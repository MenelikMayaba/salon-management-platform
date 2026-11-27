package za.co.salon.catalogue.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.salon.catalogue.entity.ServiceCategory;

import java.util.Optional;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {

    Optional<ServiceCategory> findByIdAndBusinessId(Long id, Long businessId);

    Page<ServiceCategory> findAllByBusinessId(Long businessId, Pageable pageable);
}
