package za.co.salon.sales.spi;

import za.co.salon.sales.entity.Sale;
import za.co.salon.sales.entity.SaleItem;

import java.util.List;

/**
 * Phase 7 seam: commission accrual hooks into sale completion here. The phase 5
 * implementation is a deliberate no-op - commissions do not exist yet - but the
 * call is part of the completion transaction so phase 7 only fills in the body.
 */
public interface CommissionService {

    void recordFromSale(Sale sale, List<SaleItem> items);
}
