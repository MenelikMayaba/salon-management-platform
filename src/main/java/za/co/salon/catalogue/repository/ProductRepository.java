package za.co.salon.catalogue.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.salon.catalogue.entity.Product;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndBusinessId(Long id, Long businessId);

    Page<Product> findAllByBusinessId(Long businessId, Pageable pageable);
}
