package za.co.salon.finance.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class JournalEntryResponse {

    private Long id;
    private Long entryNumber;
    private String sourceType;
    private Long sourceId;
    private LocalDate postingDate;
    private String description;
    private String status;
    private Long reversalOfId;
    private Instant postedAt;
    private List<LineResponse> lines;

    @Getter
    @Setter
    public static class LineResponse {
        private Long accountId;
        private String accountCode;
        private String accountName;
        private String description;
        private java.math.BigDecimal debit;
        private java.math.BigDecimal credit;
    }
}
