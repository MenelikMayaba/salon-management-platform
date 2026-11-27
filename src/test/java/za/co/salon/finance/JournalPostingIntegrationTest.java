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
 * Phase 4 tests #1, #2, #3, #6, #9: the posting core of the general ledger.
 * Written before implementation (RGR). Tests autowire the posting service
 * directly - there are deliberately no write endpoints for journal entries.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JournalPostingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JournalPostingService postingService;

    @Autowired
    private JournalEntryRepository entryRepository;

    @Autowired
    private JournalEntryLineRepository lineRepository;

    @Test
    void balancedEntryPostsWithSequentialNumber() throws Exception {
        long businessId = newBusiness("post.balanced");

        JournalEntry entry = postingService.post(businessId, "SALE", 1L,
                Instant.now(), "Test sale",
                List.of(new LineSpec("1000", bd("100.0000"), BigDecimal.ZERO, "cash"),
                        new LineSpec("4000", BigDecimal.ZERO, bd("100.0000"), "revenue")),
                null);

        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getEntryNumber()).isEqualTo(1L);
        assertThat(entry.getStatus()).isEqualTo(JournalEntry.Status.POSTED);
        assertThat(entry.getPostedAt()).isNotNull();

        List<JournalEntryLine> lines = lineRepository.findAllByJournalEntryId(entry.getId());
        assertThat(lines).hasSize(2);
        // DECIMAL(19,4) scale survives the round trip.
        assertThat(lines.get(0).getDebit().compareTo(bd("100.0000"))).isZero();
    }

    @Test
    void unbalancedEntryIsRejectedAndNothingPersists() throws Exception {
        long businessId = newBusiness("post.unbalanced");
        long entriesBefore = entryRepository.count();
        long linesBefore = lineRepository.count();

        assertThatThrownBy(() -> postingService.post(businessId, "SALE", 2L,
                Instant.now(), "Bad entry",
                List.of(new LineSpec("1000", bd("100.0000"), BigDecimal.ZERO, null),
                        new LineSpec("4000", BigDecimal.ZERO, bd("90.0000"), null)),
                null))
                .hasMessageContaining("balance");

        assertThat(entryRepository.count()).isEqualTo(entriesBefore);
        assertThat(lineRepository.count()).isEqualTo(linesBefore);
    }

    @Test
    void lineShapeViolationsAreRejected() throws Exception {
        long businessId = newBusiness("post.shape");

        // Both debit and credit positive on one line.
        assertThatThrownBy(() -> postingService.post(businessId, "SALE", 3L,
                Instant.now(), "Bad line",
                List.of(new LineSpec("1000", bd("50.0000"), bd("50.0000"), null),
                        new LineSpec("4000", BigDecimal.ZERO, bd("50.0000"), null)),
                null))
                .hasMessageContaining("either a debit or a credit");

        // Both zero.
        assertThatThrownBy(() -> postingService.post(businessId, "SALE", 4L,
                Instant.now(), "Empty line",
                List.of(new LineSpec("1000", BigDecimal.ZERO, BigDecimal.ZERO, null),
                        new LineSpec("4000", BigDecimal.ZERO, bd("50.0000"), null)),
                null))
                .hasMessageContaining("either a debit or a credit");

        // Fewer than two lines.
        assertThatThrownBy(() -> postingService.post(businessId, "SALE", 5L,
                Instant.now(), "One line",
                List.of(new LineSpec("1000", bd("50.0000"), BigDecimal.ZERO, null)),
                null))
                .hasMessageContaining("at least two lines");
    }

    @Test
    void unknownAccountCodeIsRejected() throws Exception {
        long businessId = newBusiness("post.unknowncode");

        assertThatThrownBy(() -> postingService.post(businessId, "SALE", 6L,
                Instant.now(), "Bad account",
                List.of(new LineSpec("9999", bd("100.0000"), BigDecimal.ZERO, null),
                        new LineSpec("4000", BigDecimal.ZERO, bd("100.0000"), null)),
                null))
                .hasMessageContaining("9999");
    }

    @Test
    void replayedSourceReturnsSameEntryWithoutDuplicate() throws Exception {
        long businessId = newBusiness("post.replay");

        JournalEntry first = postingService.post(businessId, "SALE", 77L,
                Instant.now(), "Original",
                List.of(new LineSpec("1000", bd("100.0000"), BigDecimal.ZERO, null),
                        new LineSpec("4000", BigDecimal.ZERO, bd("100.0000"), null)),
                null);
        JournalEntry replay = postingService.post(businessId, "SALE", 77L,
                Instant.now(), "Replayed with different description",
                List.of(new LineSpec("1000", bd("100.0000"), BigDecimal.ZERO, null),
                        new LineSpec("4000", BigDecimal.ZERO, bd("100.0000"), null)),
                null);

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(replay.getEntryNumber()).isEqualTo(first.getEntryNumber());
        long count = 0;
        for (JournalEntry entry : entryRepository.findAll()) {
            if (entry.getBusinessId().equals(businessId)) {
                count++;
            }
        }
        assertThat(count).as("replayed source must not double-post").isEqualTo(1);
    }

    @Test
    void postingDateUsesBusinessLocalTimezone() throws Exception {
        long businessId = newBusiness("post.tz");

        // 2025-03-31 22:30 UTC is 2025-04-01 00:30 in Africa/Johannesburg (UTC+2).
        // The posting date must follow the business-local calendar day.
        Instant eventInstant = Instant.parse("2025-03-31T22:30:00Z");
        JournalEntry entry = postingService.post(businessId, "SALE", 88L,
                eventInstant, "Timezone probe",
                List.of(new LineSpec("1000", bd("10.0000"), BigDecimal.ZERO, null),
                        new LineSpec("4000", BigDecimal.ZERO, bd("10.0000"), null)),
                null);

        assertThat(entry.getPostingDate())
                .as("business-local date, not the UTC date")
                .isEqualTo(LocalDate.of(2025, 4, 1));
    }

    private long newBusiness(String emailPrefix) throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail(emailPrefix));
        return api.createBusiness(token, "Posting Salon");
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
