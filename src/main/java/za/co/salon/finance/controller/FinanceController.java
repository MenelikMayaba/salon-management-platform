package za.co.salon.finance.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.salon.finance.dto.AccountResponse;
import za.co.salon.finance.dto.JournalEntryResponse;
import za.co.salon.finance.service.FinanceQueryService;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only finance endpoints. OWNER and ACCOUNTANT only per the 7.4 matrix -
 * even MANAGER is excluded from journal and finance views. There are no write
 * endpoints by design: posting is invoked internally by other modules.
 */
@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {

    private final FinanceQueryService financeQueryService;

    public FinanceController(FinanceQueryService financeQueryService) {
        this.financeQueryService = financeQueryService;
    }

    @GetMapping("/accounts")
    @PreAuthorize("@authz.hasRole('OWNER','ACCOUNTANT')")
    public List<AccountResponse> listAccounts() {
        return financeQueryService.listAccounts();
    }

    @GetMapping("/journal-entries")
    @PreAuthorize("@authz.hasRole('OWNER','ACCOUNTANT')")
    public Page<JournalEntryResponse> listEntries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        return financeQueryService.listEntries(from, to, pageable);
    }

    @GetMapping("/journal-entries/{id}")
    @PreAuthorize("@authz.hasRole('OWNER','ACCOUNTANT')")
    public JournalEntryResponse getEntry(@PathVariable Long id) {
        return financeQueryService.getEntryDetail(id);
    }
}
