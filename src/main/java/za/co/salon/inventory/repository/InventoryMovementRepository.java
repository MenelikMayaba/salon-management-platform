package za.co.salon.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.salon.inventory.entity.InventoryMovement;

import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    List<InventoryMovement> findAllByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    List<InventoryMovement> findAllByBusinessIdAndProductIdOrderByIdDesc(Long businessId, Long productId);
}
