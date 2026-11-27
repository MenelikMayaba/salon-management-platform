package za.co.salon.finance.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountResponse {

    private Long id;
    private String accountCode;
    private String accountName;
    private String accountType;
    private String normalBalance;
    private boolean systemAccount;
    private boolean active;
}
