package za.co.salon.inventory;

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
import za.co.salon.finance.repository.AccountRepository;
import za.co.salon.finance.repository.JournalEntryLineRepository;
import za.co.salon.finance.repository.JournalEntryRepository;
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
 * Phase 6 tests #1 and #4: the blueprint 3.6 weighted-average-cost worked
 * example end-to-end, and the unpaid-receipt / payment accounting cycle.
 * Written before implementation (RGR).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WacReceiptIntegrationTest {

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

    @Test
    void workedExampleProducesExactWacAndCogs() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("wac.example"));
        long businessId = api.createBusiness(token, "WAC Salon");
        long supplierId = createSupplier(token);
        long productId = createProduct(token);

        // 10 @ R50, then 10 @ R70 - the blueprint's own numbers.
        receivePurchase(token, supplierId, productId, 10, "50.0000");
        receivePurchase(token, supplierId, productId, 10, "70.0000");

        ProductStockBalance balance = balanceRepository.findByBusinessIdAndProductId(businessId, productId).orElseThrow();
        assertThat(balance.getQuantityOnHand().compareTo(new BigDecimal("20.0000")))
                .as("10 + 10 on hand").isZero();
        assertThat(balance.getAverageUnitCost().compareTo(new BigDecimal("60.0000")))
                .as("(10x50 + 10x70) / 20 = 60 exactly").isZero();

        // Sell 2: stock drops to 18, average unchanged, COGS = 2 x 60 = R120.
        long saleId = draftProductSale(token, productId, 2);
        mockMvc.perform(post("/api/v1/sales/" + saleId + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        ProductStockBalance after = balanceRepository.findByBusinessIdAndProductId(businessId, productId).orElseThrow();
        assertThat(after.getQuantityOnHand().compareTo(new BigDecimal("18.0000"))).isZero();
        assertThat(after.getAverageUnitCost().compareTo(new BigDecimal("60.0000")))
                .as("sales never change the average").isZero();

        List<InventoryMovement> saleMovements = movementRepository.findAllByReferenceTypeAndReferenceId("SALE", saleId);
        assertThat(saleMovements).hasSize(1);
        assertThat(saleMovements.get(0).getQuantityChange().compareTo(new BigDecimal("-2.0000"))).isZero();
        assertThat(saleMovements.get(0).getUnitCost().compareTo(new BigDecimal("60.0000"))).isZero();

        JournalEntry cogs = entryRepository.findByBusinessIdAndSourceTypeAndSourceId(businessId, "SALE_COGS", saleId)
                .orElseThrow(() -> new AssertionError("missing COGS entry"));
        assertLines(cogs, new String[][]{{"5000", "120.0000", "0"}, {"1200", "0", "120.0000"}});
    }

    @Test
    void unpaidReceiptPostsToApAndPaymentClearsIt() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("wac.ap"));
        long businessId = api.createBusiness(token, "AP Salon");
        long supplierId = createSupplier(token);
        long productId = createProduct(token);

        long purchaseId = createPurchase(token, supplierId, productId, 10, "50.0000");
        mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/receive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Unpaid receipt: DR 1200 Inventory / CR 2000 Accounts Payable, R500 each way.
        JournalEntry receipt = entryRepository
                .findByBusinessIdAndSourceTypeAndSourceId(businessId, "PURCHASE_RECEIPT", purchaseId)
                .orElseThrow(() -> new AssertionError("missing receipt entry"));
        assertLines(receipt, new String[][]{{"1200", "500.0000", "0"}, {"2000", "0", "500.0000"}});

        // Payment by EFT: DR 2000 / CR 1010 Bank, purchase flips to PAID.
        MvcResult paid = mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500.0000,\"paymentMethod\":\"EFT\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(paid.getResponse().getContentAsString()).contains("\"status\":\"PAID\"");
        assertThat(paid.getResponse().getContentAsString()).contains("\"amountPaid\":500.0000");

        JournalEntry payment = entryRepository
                .findByBusinessIdAndSourceTypeAndSourceId(businessId, "PURCHASE_PAYMENT", purchaseId)
                .orElseThrow(() -> new AssertionError("missing payment entry"));
        assertLines(payment, new String[][]{{"2000", "500.0000", "0"}, {"1010", "0", "500.0000"}});
    }

    @Test
    void overpaymentRejectedAndDraftCannotBePaid() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("wac.overpay"));
        api.createBusiness(token, "Overpay Salon");
        long supplierId = createSupplier(token);
        long productId = createProduct(token);
        long purchaseId = createPurchase(token, supplierId, productId, 2, "50.0000");

        // DRAFT cannot be paid - receiving must happen first.
        MvcResult earlyPay = mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.0000,\"paymentMethod\":\"EFT\"}"))
                .andReturn();
        assertThat(earlyPay.getResponse().getStatus()).isIn(409, 422);

        mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/receive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 100 outstanding; paying 150 must be rejected.
        MvcResult overpay = mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":150.0000,\"paymentMethod\":\"EFT\"}"))
                .andReturn();
        assertThat(overpay.getResponse().getStatus()).isIn(409, 422);
    }

    private void assertLines(JournalEntry entry, String[][] expected) {
        List<JournalEntryLine> lines = lineRepository.findAllByJournalEntryId(entry.getId());
        assertThat(lines).as(entry.getSourceType() + " line count").hasSize(expected.length);
        Map<String, BigDecimal[]> byCode = new HashMap<>();
        for (JournalEntryLine line : lines) {
            Account account = accountRepository.findById(line.getAccountId()).orElseThrow();
            byCode.put(account.getAccountCode(), new BigDecimal[]{line.getDebit(), line.getCredit()});
        }
        for (String[] expectation : expected) {
            BigDecimal[] amounts = byCode.get(expectation[0]);
            assertThat(amounts).as("line for account " + expectation[0]).isNotNull();
            assertThat(amounts[0].compareTo(new BigDecimal(expectation[1]))).as("debit on " + expectation[0]).isZero();
            assertThat(amounts[1].compareTo(new BigDecimal(expectation[2]))).as("credit on " + expectation[0]).isZero();
        }
    }

    private void receivePurchase(String token, long supplierId, long productId, int quantity, String unitCost) throws Exception {
        long purchaseId = createPurchase(token, supplierId, productId, quantity, unitCost);
        mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/receive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private long createPurchase(String token, long supplierId, long productId, int quantity, String unitCost) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/purchases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"purchaseDate\":\"2026-11-28\""
                                + ",\"items\":[{\"productId\":" + productId + ",\"quantity\":" + quantity
                                + ",\"unitCost\":" + unitCost + "}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(result.getResponse().getContentAsString());
    }

    private long createSupplier(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"BeautyWholesale\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(result.getResponse().getContentAsString());
    }

    private long createProduct(String token) throws Exception {
        MvcResult category = mockMvc.perform(post("/api/v1/product-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Retail\"}"))
                .andReturn();
        long categoryId = extractId(category.getResponse().getContentAsString());
        MvcResult product = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"Shampoo\",\"sellingPrice\":150.0000}"))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(product.getResponse().getContentAsString());
    }

    private long draftProductSale(String token, long productId, int quantity) throws Exception {
        MvcResult sale = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemType\":\"PRODUCT\",\"productId\":" + productId
                                + ",\"quantity\":" + quantity + "}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        long saleId = extractId(sale.getResponse().getContentAsString());
        BigDecimal total = new BigDecimal("150.0000").multiply(new BigDecimal(quantity));
        mockMvc.perform(post("/api/v1/sales/" + saleId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"amount\":" + total
                                + ",\"idempotencyKey\":\"pay-" + saleId + "\"}"))
                .andExpect(status().isCreated());
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
