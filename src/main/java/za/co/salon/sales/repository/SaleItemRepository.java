package za.co.salon.sales.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.salon.sales.entity.SaleItem;

import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    List<SaleItem> findAllBySaleId(Long saleId);

    List<SaleItem> findAllBySaleIdIn(List<Long> saleIds);
}
