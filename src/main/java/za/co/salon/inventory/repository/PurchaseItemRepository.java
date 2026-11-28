package za.co.salon.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.salon.inventory.entity.PurchaseItem;

import java.util.List;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {

    List<PurchaseItem> findAllByPurchaseId(Long purchaseId);

    List<PurchaseItem> findAllByPurchaseIdIn(java.util.Collection<Long> purchaseIds);
}
