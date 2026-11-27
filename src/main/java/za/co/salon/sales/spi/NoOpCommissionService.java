package za.co.salon.sales.spi;

import org.springframework.stereotype.Component;
import za.co.salon.sales.entity.Sale;
import za.co.salon.sales.entity.SaleItem;

import java.util.List;

/**
 * TODO phase 7: replace with real commission accrual against commission_rules.
 * Deliberately a no-op in phase 5 - the seam exists so completion ordering is
 * already correct when commissions land.
 */
@Component
public class NoOpCommissionService implements CommissionService {

    @Override
    public void recordFromSale(Sale sale, List<SaleItem> items) {
        // Phase 7 seam - intentionally empty.
    }
}
