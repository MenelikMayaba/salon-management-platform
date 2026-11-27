package za.co.salon.catalogue.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.salon.catalogue.entity.ProductCategory;

import java.util.Optional;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    Optional<ProductCategory> findByIdAndBusinessId(Long id, Long businessId);

    Page<ProductCategory> findAllByBusinessId(Long businessId, Pageable pageable);
}
