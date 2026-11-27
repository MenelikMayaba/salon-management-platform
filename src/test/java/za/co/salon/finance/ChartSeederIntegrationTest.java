package za.co.salon.finance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import za.co.salon.finance.entity.Account;
import za.co.salon.finance.entity.SequenceCounter;
import za.co.salon.finance.repository.AccountRepository;
import za.co.salon.finance.repository.SequenceCounterRepository;
import za.co.salon.support.TestApiHelper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 test #8: business creation seeds the chart of accounts (16 system
 * accounts, codes 1000-5400) and both sequence counters at 1. Written first (RGR).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChartSeederIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private SequenceCounterRepository sequenceCounterRepository;

    @Test
    void newBusinessIsSeededWithChartAndCounters() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("seed.owner"));
        long businessId = api.createBusiness(token, "Seeded Salon");

        List<Account> accounts = accountRepository.findAllByBusinessId(businessId);
        assertThat(accounts).as("blueprint example chart has 16 rows").hasSize(16);
        assertThat(accounts).allMatch(Account::isSystemAccount);
        assertThat(accounts).allMatch(Account::isActive);

        Account cash = findByCode(accounts, "1000");
        assertThat(cash.getAccountName()).isEqualTo("Cash on Hand");
        assertThat(cash.getAccountType()).isEqualTo(Account.AccountType.ASSET);
        assertThat(cash.getNormalBalance()).isEqualTo(Account.NormalBalance.DEBIT);

        Account vat = findByCode(accounts, "2300");
        assertThat(vat.getAccountType()).isEqualTo(Account.AccountType.LIABILITY);
        assertThat(vat.getNormalBalance()).isEqualTo(Account.NormalBalance.CREDIT);

        Account serviceRevenue = findByCode(accounts, "4000");
        assertThat(serviceRevenue.getAccountType()).isEqualTo(Account.AccountType.REVENUE);
        assertThat(serviceRevenue.getNormalBalance()).isEqualTo(Account.NormalBalance.CREDIT);

        Account cogs = findByCode(accounts, "5000");
        assertThat(cogs.getAccountType()).isEqualTo(Account.AccountType.EXPENSE);
        assertThat(cogs.getNormalBalance()).isEqualTo(Account.NormalBalance.DEBIT);

        assertThat(findByCode(accounts, "5400").getAccountName()).isEqualTo("Other Operating Expenses");

        Optional<SequenceCounter> journalCounter =
                sequenceCounterRepository.findByBusinessIdAndSequenceName(businessId, "journal_entry");
        assertThat(journalCounter).isPresent();
        assertThat(journalCounter.get().getNextValue()).isEqualTo(1L);

        Optional<SequenceCounter> saleCounter =
                sequenceCounterRepository.findByBusinessIdAndSequenceName(businessId, "sale");
        assertThat(saleCounter).isPresent();
        assertThat(saleCounter.get().getNextValue()).isEqualTo(1L);
    }

    @Test
    void secondBusinessGetsItsOwnIndependentChart() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String tokenA = api.registerUser(TestApiHelper.uniqueEmail("seed.a"));
        long businessA = api.createBusiness(tokenA, "Salon A");
        String tokenB = api.registerUser(TestApiHelper.uniqueEmail("seed.b"));
        long businessB = api.createBusiness(tokenB, "Salon B");

        List<Account> accountsA = accountRepository.findAllByBusinessId(businessA);
        List<Account> accountsB = accountRepository.findAllByBusinessId(businessB);
        assertThat(accountsA).hasSize(16);
        assertThat(accountsB).hasSize(16);
        // Same codes, but independent rows - no sharing across tenants.
        assertThat(accountsA.get(0).getId()).isNotEqualTo(accountsB.get(0).getId());
    }

    private Account findByCode(List<Account> accounts, String code) {
        for (Account account : accounts) {
            if (account.getAccountCode().equals(code)) {
                return account;
            }
        }
        throw new AssertionError("chart is missing account code " + code);
    }
}
