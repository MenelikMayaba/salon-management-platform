package za.co.salon.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import za.co.salon.inventory.entity.ProductStockBalance;
import za.co.salon.inventory.repository.ProductStockBalanceRepository;
import za.co.salon.support.TestApiHelper;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 6 test #2: two parallel receipts on the same product must serialize on
 * the balance row lock. Without FOR UPDATE both reads see the same old balance
 * and one WAC update is silently lost - this bug is invisible single-threaded,
 * which is exactly why this test exists. Deliberately NOT @Transactional:
 * real threads need real committing transactions (same exception as phases 4-5).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrentReceiptIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductStockBalanceRepository balanceRepository;

    @Test
    void parallelReceiptsSerializeToCorrectFinalWac() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("wac.race"));
        long businessId = api.createBusiness(token, "Race Receipts Salon");
        long supplierId = createSupplier(token);
        long productId = createProduct(token);

        // Two draft purchases: 10 @ 50 and 10 @ 70 - same numbers as the worked
        // example, but received CONCURRENTLY. Correct final state regardless of
        // which locks first: qty 20, avg 60.
        long purchaseA = createPurchase(token, supplierId, productId, 10, "50.0000");
        long purchaseB = createPurchase(token, supplierId, productId, 10, "70.0000");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<Integer> first = pool.submit(() -> receive(token, purchaseA, ready, go));
            Future<Integer> second = pool.submit(() -> receive(token, purchaseB, ready, go));
            ready.await();
            go.countDown();
            int statusA = first.get();
            int statusB = second.get();
            // Deadlock or serialization failure would surface as a 500 on one side.
            assertThat(statusA).as("first receive status").isEqualTo(200);
            assertThat(statusB).as("second receive status").isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }

        ProductStockBalance balance = balanceRepository.findByBusinessIdAndProductId(businessId, productId).orElseThrow();
        assertThat(balance.getQuantityOnHand().compareTo(new BigDecimal("20.0000")))
                .as("both receipts applied - no lost update").isZero();
        assertThat(balance.getAverageUnitCost().compareTo(new BigDecimal("60.0000")))
                .as("WAC weighted over BOTH receipts - a lost update would show 50 or 70").isZero();
    }

    private int receive(String token, long purchaseId, CountDownLatch ready, CountDownLatch go) {
        try {
            ready.countDown();
            go.await();
            MvcResult result = mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/receive")
                            .header("Authorization", "Bearer " + token))
                    .andReturn();
            return result.getResponse().getStatus();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
