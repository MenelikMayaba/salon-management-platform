package za.co.salon.finance.posting;

import java.math.BigDecimal;

/**
 * One intended journal line, by account CODE - callers think in chart codes
 * ("1200"), never database ids, so module code stays readable.
 */
public class LineSpec {

    private final String accountCode;
    private final BigDecimal debit;
    private final BigDecimal credit;
    private final String description;

    public LineSpec(String accountCode, BigDecimal debit, BigDecimal credit, String description) {
        this.accountCode = accountCode;
        this.debit = debit;
        this.credit = credit;
        this.description = description;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public String getDescription() {
        return description;
    }
}
