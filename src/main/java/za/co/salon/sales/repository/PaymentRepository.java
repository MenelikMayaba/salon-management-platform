package za.co.salon.sales.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.salon.sales.entity.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findAllBySaleId(Long saleId);

    Optional<Payment> findByBusinessIdAndIdempotencyKey(Long businessId, String idempotencyKey);
}
