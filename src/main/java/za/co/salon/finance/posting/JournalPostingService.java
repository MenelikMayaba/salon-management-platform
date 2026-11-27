package za.co.salon.finance.posting;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.salon.business.entity.Business;
import za.co.salon.business.repository.BusinessRepository;
import za.co.salon.finance.accounting.SequenceNumberService;
import za.co.salon.finance.entity.Account;
import za.co.salon.finance.entity.JournalEntry;
import za.co.salon.finance.entity.JournalEntryLine;
import za.co.salon.finance.repository.AccountRepository;
import za.co.salon.finance.repository.JournalEntryLineRepository;
import za.co.salon.finance.repository.JournalEntryRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The ONLY writer of journal_entries and journal_entry_lines in the system
 * (blueprint section 5). Every financial event in every module funnels through
 * here, which is what makes the ledger trustworthy: balance validation, line
 * shape, same-business account resolution, gapless numbering, immutability and
 * source-based idempotency all live in exactly one place.
 */
@Service
public class JournalPostingService {

    private final JournalEntryRepository entryRepository;
    private final JournalEntryLineRepository lineRepository;
    private final AccountRepository accountRepository;
    private final BusinessRepository businessRepository;
    private final SequenceNumberService sequenceNumberService;

    public JournalPostingService(JournalEntryRepository entryRepository,
                                 JournalEntryLineRepository lineRepository,
                                 AccountRepository accountRepository,
                                 BusinessRepository businessRepository,
                                 SequenceNumberService sequenceNumberService) {
        this.entryRepository = entryRepository;
        this.lineRepository = lineRepository;
        this.accountRepository = accountRepository;
        this.businessRepository = businessRepository;
        this.sequenceNumberService = sequenceNumberService;
    }

    /**
     * Posts a balanced journal entry for a financial event. Runs inside the
     * caller's transaction when one exists, so the entry and its business
     * event commit or roll back together.
     *
     * Idempotency: a replayed (sourceType, sourceId) returns the original entry
     * instead of double-posting. The unique constraint is the race backstop.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public JournalEntry post(Long businessId, String sourceType, Long sourceId, Instant eventInstant,
                             String description, List<LineSpec> lines, Long createdByUserId) {
        if (sourceId != null) {
            Optional<JournalEntry> existing =
                    entryRepository.findByBusinessIdAndSourceTypeAndSourceId(businessId, sourceType, sourceId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        validateLineShapes(lines);
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        List<ResolvedLine> resolved = new ArrayList<>();
        for (LineSpec line : lines) {
            Account account = resolveAccount(businessId, line.getAccountCode());
            totalDebit = totalDebit.add(line.getDebit());
            totalCredit = totalCredit.add(line.getCredit());
            resolved.add(new ResolvedLine(account, line));
        }
        // The balance invariant is validated inside the posting transaction (2.4).
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Journal entry does not balance: debits " + totalDebit + " != credits " + totalCredit);
        }

        JournalEntry entry = new JournalEntry();
        entry.setBusinessId(businessId);
        entry.setEntryNumber(sequenceNumberService.next(businessId, "journal_entry"));
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        entry.setPostingDate(toBusinessLocalDate(businessId, eventInstant));
        entry.setDescription(description);
        entry.setStatus(JournalEntry.Status.POSTED);
        entry.setCreatedBy(createdByUserId);
        entry.setPostedAt(Instant.now());
        entryRepository.save(entry);

        for (ResolvedLine line : resolved) {
            JournalEntryLine entity = new JournalEntryLine();
            entity.setBusinessId(businessId);
            entity.setJournalEntryId(entry.getId());
            entity.setAccountId(line.account.getId());
            entity.setDescription(line.spec.getDescription());
            entity.setDebit(line.spec.getDebit());
            entity.setCredit(line.spec.getCredit());
            lineRepository.save(entity);
        }
        return entry;
    }

    /**
     * The ONLY mutation path in the ledger: corrections never edit a posted
     * entry, they post a new entry with every line inverted. The original is
     * marked REVERSED and becomes final - a second reversal of the same entry
     * is refused because its status is no longer POSTED. The reversal itself
     * carries sourceType REVERSAL + sourceId of the original, so the unique
     * (business, source_type, source_id) constraint makes double-reversal
     * impossible even under races.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public JournalEntry reverse(Long journalEntryId, LocalDate reversalDate, String description) {
        JournalEntry original = entryRepository.findById(journalEntryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journal entry not found"));
        if (original.getStatus() != JournalEntry.Status.POSTED) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only POSTED entries can be reversed; this entry is " + original.getStatus());
        }

        JournalEntry reversal = new JournalEntry();
        reversal.setBusinessId(original.getBusinessId());
        reversal.setEntryNumber(sequenceNumberService.next(original.getBusinessId(), "journal_entry"));
        reversal.setSourceType("REVERSAL");
        reversal.setSourceId(original.getId());
        reversal.setPostingDate(reversalDate);
        reversal.setDescription(description);
        reversal.setStatus(JournalEntry.Status.POSTED);
        reversal.setReversalOfId(original.getId());
        reversal.setPostedAt(Instant.now());
        entryRepository.save(reversal);

        for (JournalEntryLine originalLine : lineRepository.findAllByJournalEntryId(original.getId())) {
            JournalEntryLine inverted = new JournalEntryLine();
            inverted.setBusinessId(original.getBusinessId());
            inverted.setJournalEntryId(reversal.getId());
            inverted.setAccountId(originalLine.getAccountId());
            inverted.setDescription(originalLine.getDescription());
            inverted.setDebit(originalLine.getCredit());
            inverted.setCredit(originalLine.getDebit());
            lineRepository.save(inverted);
        }

        original.setStatus(JournalEntry.Status.REVERSED);
        entryRepository.save(original);
        return reversal;
    }

    /**
     * Each line carries exactly one of debit/credit (never both, never neither),
     * and an entry needs at least two lines to mean anything.
     */
    private void validateLineShapes(List<LineSpec> lines) {
        if (lines == null || lines.size() < 2) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A journal entry needs at least two lines");
        }
        for (LineSpec line : lines) {
            boolean hasDebit = line.getDebit() != null && line.getDebit().compareTo(BigDecimal.ZERO) > 0;
            boolean hasCredit = line.getCredit() != null && line.getCredit().compareTo(BigDecimal.ZERO) > 0;
            if (hasDebit == hasCredit) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Each line must carry either a debit or a credit, never both, never neither");
            }
        }
    }

    private Account resolveAccount(Long businessId, String accountCode) {
        Account account = accountRepository.findByBusinessIdAndAccountCode(businessId, accountCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Unknown account code for this business: " + accountCode));
        if (!account.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Account is inactive: " + accountCode);
        }
        return account;
    }

    /**
     * Accounting dates follow the business-local calendar (blueprint 2.5): a sale
     * completed at 00:30 in the salon's timezone belongs to that local day, even
     * though the UTC instant still sits on the previous day.
     */
    private LocalDate toBusinessLocalDate(Long businessId, Instant eventInstant) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));
        ZoneId zone = ZoneId.of(business.getTimezone());
        return eventInstant.atZone(zone).toLocalDate();
    }

    private static class ResolvedLine {
        private final Account account;
        private final LineSpec spec;

        private ResolvedLine(Account account, LineSpec spec) {
            this.account = account;
            this.spec = spec;
        }
    }
}
