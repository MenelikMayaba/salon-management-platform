package za.co.salon.finance;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import za.co.salon.finance.entity.JournalEntry;
import za.co.salon.finance.posting.JournalPostingService;
import za.co.salon.finance.posting.LineSpec;
import za.co.salon.support.TestApiHelper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 slice 5: read-only finance endpoints. OWNER and ACCOUNTANT only -
 * even MANAGER is excluded per the 7.4 matrix. Written before implementation (RGR).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FinanceEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JournalPostingService postingService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Test
    void accountsEndpointIsOwnerAndAccountantOnly() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String ownerToken = api.registerUser(TestApiHelper.uniqueEmail("fin.acct.owner"));
        api.createBusiness(ownerToken, "Fin Salon");
        String accountantEmail = TestApiHelper.uniqueEmail("fin.acct.acc");
        String accountantToken = api.registerUser(accountantEmail);
        api.addMember(ownerToken, accountantEmail, "ACCOUNTANT");

        MvcResult ownerList = mockMvc.perform(get("/api/v1/finance/accounts")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(ownerList.getResponse().getContentAsString()).contains("Cash on Hand");

        mockMvc.perform(get("/api/v1/finance/accounts")
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isOk());

        // The matrix excludes even MANAGER from finance views.
        String realManagerEmail = TestApiHelper.uniqueEmail("fin.acct.realmgr");
        String realManagerToken = api.registerUser(realManagerEmail);
        api.addMember(ownerToken, realManagerEmail, "MANAGER");
        mockMvc.perform(get("/api/v1/finance/accounts")
                        .header("Authorization", "Bearer " + realManagerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void journalEntriesListIsPagedAndDateFiltered() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("fin.list"));
        long businessId = api.createBusiness(token, "List Salon");

        postSale(businessId, 1L, "2025-03-01T10:00:00Z");
        postSale(businessId, 2L, "2025-03-15T10:00:00Z");

        MvcResult all = mockMvc.perform(get("/api/v1/finance/journal-entries")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(all.getResponse().getContentAsString()).contains("\"totalElements\":2");

        // Date filter on posting_date: the March 1 entry falls outside from=2025-03-10.
        MvcResult filtered = mockMvc.perform(get("/api/v1/finance/journal-entries?from=2025-03-10&to=2025-03-31")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(filtered.getResponse().getContentAsString()).contains("\"totalElements\":1");
    }

    @Test
    void detailIncludesLinesAndIsTenantIsolated() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String tokenA = api.registerUser(TestApiHelper.uniqueEmail("fin.det.a"));
        long businessA = api.createBusiness(tokenA, "Detail A");
        JournalEntry entry = postSale(businessA, 5L, "2025-03-05T10:00:00Z");

        MvcResult detail = mockMvc.perform(get("/api/v1/finance/journal-entries/" + entry.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        String body = detail.getResponse().getContentAsString();
        assertThat(body).contains("lines");
        assertThat(body).contains("Cash on Hand");

        String tokenB = api.registerUser(TestApiHelper.uniqueEmail("fin.det.b"));
        api.createBusiness(tokenB, "Detail B");
        MvcResult crossTenant = mockMvc.perform(get("/api/v1/finance/journal-entries/" + entry.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andReturn();
        assertThat(crossTenant.getResponse().getStatus()).isIn(403, 404);
    }

    @Test
    void listQueryCountDoesNotScaleWithRows() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("fin.qc"));
        long businessId = api.createBusiness(token, "QC Salon");

        postSale(businessId, 1L, "2025-03-01T10:00:00Z");
        long countSmall = countStatementsDuringList(token);

        for (long index = 2; index <= 5; index++) {
            postSale(businessId, index, "2025-03-01T10:00:00Z");
        }
        long countLarge = countStatementsDuringList(token);

        assertThat(countLarge)
                .as("query count must not grow with rows (small=%d, large=%d)", countSmall, countLarge)
                .isEqualTo(countSmall);
    }

    private JournalEntry postSale(long businessId, long sourceId, String instant) {
        return postingService.post(businessId, "SALE", sourceId,
                Instant.parse(instant), "Sale " + sourceId,
                List.of(new LineSpec("1000", new BigDecimal("100.0000"), BigDecimal.ZERO, null),
                        new LineSpec("4000", BigDecimal.ZERO, new BigDecimal("100.0000"), null)),
                null);
    }

    private long countStatementsDuringList(String token) throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long before = statistics.getPrepareStatementCount();
        mockMvc.perform(get("/api/v1/finance/journal-entries")
                .header("Authorization", "Bearer " + token));
        return statistics.getPrepareStatementCount() - before;
    }
}
