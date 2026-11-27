package za.co.salon.finance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import za.co.salon.finance.entity.JournalEntry;
import za.co.salon.finance.entity.JournalEntryLine;
import za.co.salon.finance.posting.JournalPostingService;
import za.co.salon.finance.posting.LineSpec;
import za.co.salon.finance.repository.JournalEntryLineRepository;
import za.co.salon.finance.repository.JournalEntryRepository;
import za.co.salon.support.TestApiHelper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4 tests #4 and #5: reversal is the ONLY mutation path for posted entries,
 * and a reversed entry cannot be mutated further. Written before implementation (RGR).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReversalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JournalPostingService postingService;

    @Autowired
    private JournalEntryRepository entryRepository;

    @Autowired
    private JournalEntryLineRepository lineRepository;

    @Test
    void reversalInvertsLinesAndNetsLedgerToZero() throws Exception {
        long businessId = newBusiness("rev.basic");

        JournalEntry original = postingService.post(businessId, "SALE", 10L,
                Instant.now(), "Original sale",
                List.of(new LineSpec("1000", bd("250.0000"), BigDecimal.ZERO, "cash in"),
                        new LineSpec("4000", BigDecimal.ZERO, bd("250.0000"), "revenue")),
                null);

        JournalEntry reversal = postingService.reverse(original.getId(),
                LocalDate.of(2025, 6, 15), "Correcting the sale");

        assertThat(reversal.getId()).isNotEqualTo(original.getId());
        assertThat(reversal.getStatus()).isEqualTo(JournalEntry.Status.POSTED);
        assertThat(reversal.getReversalOfId()).isEqualTo(original.getId());
        assertThat(reversal.getPostingDate()).isEqualTo(LocalDate.of(2025, 6, 15));

        JournalEntry reloadedOriginal = entryRepository.findById(original.getId()).orElseThrow();
        assertThat(reloadedOriginal.getStatus()).isEqualTo(JournalEntry.Status.REVERSED);

        // Per-account inversion: every original line has a mirror line in the
        // reversal with the same account and debit/credit swapped. Netting all
        // lines together would pass trivially for any balanced pair, so the
        // assertion must be per account.
        List<JournalEntryLine> originalLines = lineRepository.findAllByJournalEntryId(original.getId());
        List<JournalEntryLine> reversalLines = lineRepository.findAllByJournalEntryId(reversal.getId());
        assertThat(reversalLines).hasSize(2);
        for (JournalEntryLine originalLine : originalLines) {
            JournalEntryLine mirror = null;
            for (JournalEntryLine reversalLine : reversalLines) {
                if (reversalLine.getAccountId().equals(originalLine.getAccountId())) {
                    mirror = reversalLine;
                }
            }
            assertThat(mirror).as("reversal must mirror every original line").isNotNull();
            assertThat(mirror.getDebit().compareTo(originalLine.getCredit()))
                    .as("reversal debit equals original credit").isZero();
            assertThat(mirror.getCredit().compareTo(originalLine.getDebit()))
                    .as("reversal credit equals original debit").isZero();
        }
    }

    @Test
    void reversingAnAlreadyReversedEntryIsRefused() throws Exception {
        long businessId = newBusiness("rev.double");

        JournalEntry original = postingService.post(businessId, "SALE", 11L,
                Instant.now(), "Sale",
                List.of(new LineSpec("1000", bd("100.0000"), BigDecimal.ZERO, null),
                        new LineSpec("4000", BigDecimal.ZERO, bd("100.0000"), null)),
                null);
        postingService.reverse(original.getId(), LocalDate.now(), "First reversal");

        // The only mutation path enforces state: a REVERSED entry is final.
        assertThatThrownBy(() -> postingService.reverse(original.getId(), LocalDate.now(), "Second reversal"))
                .hasMessageContaining("POSTED");
    }

    private long newBusiness(String emailPrefix) throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail(emailPrefix));
        return api.createBusiness(token, "Reversal Salon");
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
