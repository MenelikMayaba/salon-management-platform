package za.co.salon.finance.accounting;

import org.springframework.stereotype.Component;
import za.co.salon.business.entity.Business;
import za.co.salon.business.onboarding.BusinessOnboardingHandler;
import za.co.salon.finance.entity.Account;
import za.co.salon.finance.entity.SequenceCounter;
import za.co.salon.finance.repository.AccountRepository;
import za.co.salon.finance.repository.SequenceCounterRepository;

/**
 * Seeds the blueprint 3.8 example chart (16 system accounts, codes 1000-5400)
 * plus the journal_entry and sale counters when a business is created.
 * Normal balance follows the type: ASSET/EXPENSE debit-normal,
 * LIABILITY/EQUITY/REVENUE credit-normal.
 */
@Component
public class ChartOfAccountsSeeder implements BusinessOnboardingHandler {

    private static final String[][] CHART = {
            {"1000", "Cash on Hand", "ASSET"},
            {"1010", "Bank Account", "ASSET"},
            {"1100", "Accounts Receivable", "ASSET"},
            {"1200", "Inventory", "ASSET"},
            {"2000", "Accounts Payable", "LIABILITY"},
            {"2100", "Customer Deposits", "LIABILITY"},
            {"2200", "Commission Payable", "LIABILITY"},
            {"2300", "VAT Payable", "LIABILITY"},
            {"3000", "Owner's Equity", "EQUITY"},
            {"4000", "Service Revenue", "REVENUE"},
            {"4100", "Product Sales Revenue", "REVENUE"},
            {"5000", "Cost of Goods Sold", "EXPENSE"},
            {"5100", "Rent Expense", "EXPENSE"},
            {"5200", "Electricity Expense", "EXPENSE"},
            {"5300", "Commission Expense", "EXPENSE"},
            {"5400", "Other Operating Expenses", "EXPENSE"}
    };

    private final AccountRepository accountRepository;
    private final SequenceCounterRepository sequenceCounterRepository;

    public ChartOfAccountsSeeder(AccountRepository accountRepository,
                                 SequenceCounterRepository sequenceCounterRepository) {
        this.accountRepository = accountRepository;
        this.sequenceCounterRepository = sequenceCounterRepository;
    }

    @Override
    public void onBusinessCreated(Business business) {
        for (String[] row : CHART) {
            accountRepository.save(buildAccount(business.getId(), row));
        }
        seedCounter(business.getId(), "journal_entry");
        seedCounter(business.getId(), "sale");
    }

    private Account buildAccount(Long businessId, String[] row) {
        Account.AccountType type = Account.AccountType.valueOf(row[2]);
        Account account = new Account();
        account.setBusinessId(businessId);
        account.setAccountCode(row[0]);
        account.setAccountName(row[1]);
        account.setAccountType(type);
        account.setNormalBalance(normalBalanceOf(type));
        account.setSystemAccount(true);
        account.setActive(true);
        return account;
    }

    private Account.NormalBalance normalBalanceOf(Account.AccountType type) {
        if (type == Account.AccountType.ASSET || type == Account.AccountType.EXPENSE) {
            return Account.NormalBalance.DEBIT;
        }
        return Account.NormalBalance.CREDIT;
    }

    private void seedCounter(Long businessId, String sequenceName) {
        SequenceCounter counter = new SequenceCounter();
        counter.setBusinessId(businessId);
        counter.setSequenceName(sequenceName);
        counter.setNextValue(1L);
        sequenceCounterRepository.save(counter);
    }
}
