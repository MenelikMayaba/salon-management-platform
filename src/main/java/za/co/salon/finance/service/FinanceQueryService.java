package za.co.salon.finance.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.salon.finance.dto.AccountResponse;
import za.co.salon.finance.dto.JournalEntryResponse;
import za.co.salon.finance.entity.Account;
import za.co.salon.finance.entity.JournalEntry;
import za.co.salon.finance.entity.JournalEntryLine;
import za.co.salon.finance.repository.AccountRepository;
import za.co.salon.finance.repository.JournalEntryLineRepository;
import za.co.salon.finance.repository.JournalEntryRepository;
import za.co.salon.shared.security.BusinessAuthorizationService;
import za.co.salon.shared.security.BusinessContext;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only finance queries. All writing goes through JournalPostingService -
 * this service never mutates the ledger.
 */
@Service
public class FinanceQueryService {

    private final AccountRepository accountRepository;
    private final JournalEntryRepository entryRepository;
    private final JournalEntryLineRepository lineRepository;
    private final BusinessAuthorizationService authz;
    private final BusinessContext businessContext;

    public FinanceQueryService(AccountRepository accountRepository,
                               JournalEntryRepository entryRepository,
                               JournalEntryLineRepository lineRepository,
                               BusinessAuthorizationService authz,
                               BusinessContext businessContext) {
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
        this.lineRepository = lineRepository;
        this.authz = authz;
        this.businessContext = businessContext;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts() {
        List<AccountResponse> responses = new ArrayList<>();
        for (Account account : accountRepository.findAllByBusinessId(businessContext.getBusinessId())) {
            responses.add(toAccountResponse(account));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public Page<JournalEntryResponse> listEntries(LocalDate from, LocalDate to, Pageable pageable) {
        Long businessId = businessContext.getBusinessId();
        Page<JournalEntry> page;
        if (from != null && to != null) {
            page = entryRepository.findByBusinessIdAndPostingDateBetween(businessId, from, to, pageable);
        } else {
            page = entryRepository.findByBusinessId(businessId, pageable);
        }
        // The list view stays flat (no lines), so query count cannot scale with rows.
        return page.map(this::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public JournalEntryResponse getEntryDetail(Long id) {
        JournalEntry entry = entryRepository.findByIdAndBusinessId(id, businessContext.getBusinessId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journal entry not found"));
        authz.assertBusinessOwnership(entry);
        JournalEntryResponse response = toSummaryResponse(entry);
        response.setLines(buildLineResponses(entry.getId()));
        return response;
    }

    /**
     * Lines carry account ids; names are batch-fetched in one query (rule 15).
     */
    private List<JournalEntryResponse.LineResponse> buildLineResponses(Long entryId) {
        List<JournalEntryLine> lines = lineRepository.findAllByJournalEntryId(entryId);
        Set<Long> accountIds = new HashSet<>();
        for (JournalEntryLine line : lines) {
            accountIds.add(line.getAccountId());
        }
        Map<Long, Account> accountsById = new HashMap<>();
        for (Account account : accountRepository.findAllById(accountIds)) {
            accountsById.put(account.getId(), account);
        }
        List<JournalEntryResponse.LineResponse> responses = new ArrayList<>();
        for (JournalEntryLine line : lines) {
            Account account = accountsById.get(line.getAccountId());
            JournalEntryResponse.LineResponse lineResponse = new JournalEntryResponse.LineResponse();
            lineResponse.setAccountId(line.getAccountId());
            if (account != null) {
                lineResponse.setAccountCode(account.getAccountCode());
                lineResponse.setAccountName(account.getAccountName());
            }
            lineResponse.setDescription(line.getDescription());
            lineResponse.setDebit(line.getDebit());
            lineResponse.setCredit(line.getCredit());
            responses.add(lineResponse);
        }
        return responses;
    }

    private AccountResponse toAccountResponse(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setAccountCode(account.getAccountCode());
        response.setAccountName(account.getAccountName());
        response.setAccountType(account.getAccountType().name());
        response.setNormalBalance(account.getNormalBalance().name());
        response.setSystemAccount(account.isSystemAccount());
        response.setActive(account.isActive());
        return response;
    }

    private JournalEntryResponse toSummaryResponse(JournalEntry entry) {
        JournalEntryResponse response = new JournalEntryResponse();
        response.setId(entry.getId());
        response.setEntryNumber(entry.getEntryNumber());
        response.setSourceType(entry.getSourceType());
        response.setSourceId(entry.getSourceId());
        response.setPostingDate(entry.getPostingDate());
        response.setDescription(entry.getDescription());
        response.setStatus(entry.getStatus().name());
        response.setReversalOfId(entry.getReversalOfId());
        response.setPostedAt(entry.getPostedAt());
        return response;
    }
}
