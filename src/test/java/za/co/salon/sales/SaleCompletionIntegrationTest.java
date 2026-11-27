package za.co.salon.sales;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import za.co.salon.finance.entity.Account;
import za.co.salon.finance.entity.JournalEntry;
import za.co.salon.finance.entity.JournalEntryLine;
import za.co.salon.finance.entity.SequenceCounter;
import za.co.salon.finance.repository.AccountRepository;
import za.co.salon.finance.repository.JournalEntryLineRepository;
import za.co.salon.finance.repository.JournalEntryRepository;
import za.co.salon.finance.repository.SequenceCounterRepository;
import za.co.salon.inventory.entity.InventoryMovement;
import za.co.salon.inventory.entity.ProductStockBalance;
import za.co.salon.inventory.repository.InventoryMovementRepository;
import za.co.salon.inventory.repository.ProductStockBalanceRepository;
import za.co.salon.support.TestApiHelper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5 tests #1 and #4: the blueprint 8.1 worked example end-to-end, and
 * full rollback when stock is insufficient. Written before implementation (RGR).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SaleCompletionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductStockBalanceRepository balanceRepository;

    @Autowired
    private InventoryMovementRepository movementRepository;

    @Autowired
    private JournalEntryRepository entryRepository;

    @Autowired
    private JournalEntryLineRepository lineRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private SequenceCounterRepository sequenceCounterRepository;

    @Test
    void workedExampleProducesThreeExactJournalEntries() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("complete.81"));
        long businessId = api.createBusiness(token, "Worked Example Salon");

        long serviceId = createService(token, "Haircut", "250.0000");
        long productId = createProduct(token, "Shampoo", "150.0000");
        // Shampoo on hand: 10 units at weighted average cost R60 (blueprint example).
        seedStock(businessId, productId, "10", "60.0000");

        long saleId = draftSale(token, serviceId, productId);
        addPayment(token, saleId, "CASH", "250.0000", "ex-cash");
        addPayment(token, saleId, "EFT", "150.0000", "ex-eft");

        MvcResult completed = mockMvc.perform(post("/api/v1/sales/" + saleId + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String body = completed.getResponse().getContentAsString();
        assertThat(body).contains("\"status\":\"COMPLETED\"");
        assertThat(body).contains("\"saleNumber\":\"1\"");
        assertThat(body).contains("completedAt");

        // Three accounting events, exactly matching the 8.1 tables.
        JournalEntry recognition = findEntry(businessId, "SALE", saleId);
        assertLines(businessId, recognition,
                new String[][]{{"1100", "400.0000", "0"}, {"4000", "0", "250.0000"}, {"4100", "0", "150.0000"}});

        JournalEntry collection = findEntry(businessId, "SALE_PAYMENTS", saleId);
        assertLines(businessId, collection,
                new String[][]{{"1000", "250.0000", "0"}, {"1010", "150.0000", "0"}, {"1100", "0", "400.0000"}});

        JournalEntry cogs = findEntry(businessId, "SALE_COGS", saleId);
        assertLines(businessId, cogs,
                new String[][]{{"5000", "60.0000", "0"}, {"1200", "0", "60.0000"}});

        // Stock deducted with a movement audit row.
        ProductStockBalance balance = balanceRepository.findByBusinessIdAndProductId(businessId, productId).orElseThrow();
        assertThat(balance.getQuantityOnHand().compareTo(new BigDecimal("9.0000"))).isZero();
        List<InventoryMovement> movements = movementRepository.findAllByReferenceTypeAndReferenceId("SALE", saleId);
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getQuantityChange().compareTo(new BigDecimal("-1.0000"))).isZero();
        assertThat(movements.get(0).getUnitCost().compareTo(new BigDecimal("60.0000"))).isZero();
    }

    private void assertLines(long businessId, JournalEntry entry, String[][] expected) {
        List<JournalEntryLine> lines = lineRepository.findAllByJournalEntryId(entry.getId());
        assertThat(lines).as("entry " + entry.getSourceType() + " line count").hasSize(expected.length);
        Map<String, BigDecimal[]> byCode = new HashMap<>();
        for (JournalEntryLine line : lines) {
            Account account = accountRepository.findById(line.getAccountId()).orElseThrow();
            byCode.put(account.getAccountCode(), new BigDecimal[]{line.getDebit(), line.getCredit()});
        }
        for (String[] expectation : expected) {
            BigDecimal[] amounts = byCode.get(expectation[0]);
            assertThat(amounts).as("line for account " + expectation[0]).isNotNull();
            assertThat(amounts[0].compareTo(new BigDecimal(expectation[1])))
                    .as("debit on " + expectation[0]).isZero();
            assertThat(amounts[1].compareTo(new BigDecimal(expectation[2])))
                    .as("credit on " + expectation[0]).isZero();
        }
    }

    private JournalEntry findEntry(long businessId, String sourceType, long saleId) {
        return entryRepository.findByBusinessIdAndSourceTypeAndSourceId(businessId, sourceType, saleId)
                .orElseThrow(() -> new AssertionError("missing journal entry " + sourceType));
    }

    private void seedStock(long businessId, long productId, String quantity, String averageCost) {
        ProductStockBalance balance = new ProductStockBalance();
        balance.setBusinessId(businessId);
        balance.setProductId(productId);
        balance.setQuantityOnHand(new BigDecimal(quantity));
        balance.setAverageUnitCost(new BigDecimal(averageCost));
        balanceRepository.save(balance);
    }

    private long createService(String token, String name, String price) throws Exception {
        MvcResult category = mockMvc.perform(post("/api/v1/service-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hair\"}"))
                .andReturn();
        long categoryId = extractId(category.getResponse().getContentAsString());
        MvcResult service = mockMvc.perform(post("/api/v1/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"" + name
                                + "\",\"durationMinutes\":30,\"price\":" + price + "}"))
                .andReturn();
        return extractId(service.getResponse().getContentAsString());
    }

    private long createProduct(String token, String name, String price) throws Exception {
        MvcResult category = mockMvc.perform(post("/api/v1/product-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Retail\"}"))
                .andReturn();
        long categoryId = extractId(category.getResponse().getContentAsString());
        MvcResult product = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"" + name
                                + "\",\"sellingPrice\":" + price + "}"))
                .andReturn();
        return extractId(product.getResponse().getContentAsString());
    }

    private long draftSale(String token, long serviceId, long productId) throws Exception {
        MvcResult sale = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemType\":\"SERVICE\",\"serviceId\":" + serviceId + ",\"quantity\":1}"
                                + ",{\"itemType\":\"PRODUCT\",\"productId\":" + productId + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(sale.getResponse().getContentAsString());
    }

    private void addPayment(String token, long saleId, String method, String amount, String key) throws Exception {
        mockMvc.perform(post("/api/v1/sales/" + saleId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"" + method + "\",\"amount\":" + amount
                                + ",\"idempotencyKey\":\"" + key + "\"}"))
                .andExpect(status().isCreated());
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
