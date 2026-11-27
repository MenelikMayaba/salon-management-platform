package za.co.salon.sales;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import za.co.salon.finance.entity.JournalEntry;
import za.co.salon.finance.entity.JournalEntryLine;
import za.co.salon.finance.repository.JournalEntryLineRepository;
import za.co.salon.finance.repository.JournalEntryRepository;
import za.co.salon.support.TestApiHelper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5 test #6 plus void semantics: refund reverses the recognition entry so
 * the ledger nets to zero; void applies to drafts only. Written before implementation (RGR).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefundIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JournalEntryRepository entryRepository;

    @Autowired
    private JournalEntryLineRepository lineRepository;

    @Test
    void fullRefundReversesRecognitionAndNetsLedgerToZero() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("refund.full"));
        long businessId = api.createBusiness(token, "Refund Salon");
        long saleId = completedServiceSale(token, "250.0000");

        MvcResult refunded = mockMvc.perform(post("/api/v1/sales/" + saleId + "/refunds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":250.0000,\"reason\":\"Customer unhappy\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(refunded.getResponse().getContentAsString()).contains("COMPLETED");

        JournalEntry recognition = entryRepository
                .findByBusinessIdAndSourceTypeAndSourceId(businessId, "SALE", saleId).orElseThrow();
        assertThat(recognition.getStatus()).isEqualTo(JournalEntry.Status.REVERSED);

        JournalEntry reversal = entryRepository
                .findByBusinessIdAndSourceTypeAndSourceId(businessId, "REVERSAL", recognition.getId()).orElseThrow();
        assertThat(reversal.getStatus()).isEqualTo(JournalEntry.Status.POSTED);

        // Ledger nets to zero: every line across both entries cancels its mirror.
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        List<JournalEntryLine> lines = lineRepository.findAllByJournalEntryId(recognition.getId());
        lines.addAll(lineRepository.findAllByJournalEntryId(reversal.getId()));
        for (JournalEntryLine line : lines) {
            debitTotal = debitTotal.add(line.getDebit());
            creditTotal = creditTotal.add(line.getCredit());
        }
        assertThat(debitTotal.compareTo(creditTotal)).as("ledger must net to zero after full refund").isZero();
    }

    @Test
    void refundRejectsExcessiveAmountAndNonCompletedSale() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("refund.guard"));
        api.createBusiness(token, "Guard Salon");
        long saleId = completedServiceSale(token, "100.0000");

        // More than the sale total.
        MvcResult tooMuch = mockMvc.perform(post("/api/v1/sales/" + saleId + "/refunds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":150.0000,\"reason\":\"x\"}"))
                .andReturn();
        assertThat(tooMuch.getResponse().getStatus()).isIn(400, 422);
    }

    @Test
    void voidAppliesToDraftsOnly() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("void.basic"));
        api.createBusiness(token, "Void Salon");
        long draftId = draftServiceSale(token, "100.0000");

        MvcResult voided = mockMvc.perform(post("/api/v1/sales/" + draftId + "/void")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(voided.getResponse().getContentAsString()).contains("\"status\":\"VOIDED\"");

        // A completed sale can never be voided - refund is the only correction path.
        long completedId = completedServiceSale(token, "100.0000");
        MvcResult result = mockMvc.perform(post("/api/v1/sales/" + completedId + "/void")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isIn(409, 422);
    }

    private long draftServiceSale(String token, String price) throws Exception {
        MvcResult category = mockMvc.perform(post("/api/v1/service-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hair\"}"))
                .andReturn();
        long categoryId = extractId(category.getResponse().getContentAsString());
        MvcResult service = mockMvc.perform(post("/api/v1/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"Cut\""
                                + ",\"durationMinutes\":30,\"price\":" + price + "}"))
                .andReturn();
        long serviceId = extractId(service.getResponse().getContentAsString());
        MvcResult sale = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemType\":\"SERVICE\",\"serviceId\":" + serviceId
                                + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(sale.getResponse().getContentAsString());
    }

    private long completedServiceSale(String token, String price) throws Exception {
        long saleId = draftServiceSale(token, price);
        mockMvc.perform(post("/api/v1/sales/" + saleId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"amount\":" + price
                                + ",\"idempotencyKey\":\"pay-" + saleId + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/sales/" + saleId + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return saleId;
    }

    private long extractId(String json) {
        int index = json.indexOf("\"id\":");
        int start = index + 5;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }
}
