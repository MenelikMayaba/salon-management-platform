package za.co.salon.business.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.salon.business.entity.Business;

public interface BusinessRepository extends JpaRepository<Business, Long> {
}
