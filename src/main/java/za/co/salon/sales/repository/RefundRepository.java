package za.co.salon.sales.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.salon.sales.entity.Refund;

import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findAllBySaleId(Long saleId);
}
