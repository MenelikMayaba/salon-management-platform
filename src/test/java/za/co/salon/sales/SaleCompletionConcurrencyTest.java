package za.co.salon.sales;

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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5 test #8: concurrent completions of two sales in the same business
 * must get distinct, sequential sale numbers - and stock deduction must never
 * oversell. Deliberately NOT @Transactional (same exception as the phase 4
 * concurrency test): real threads need real committing transactions.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SaleCompletionConcurrencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductStockBalanceRepository balanceRepository;

    @Test
    void concurrentCompletionsGetDistinctSaleNumbersAndConsistentStock() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("complete.race"));
        long businessId = api.createBusiness(token, "Race Salon");

        long productId = createProduct(token, "Shampoo", "150.0000");
        // Exactly 2 units: two completions of 1 unit each must BOTH succeed and the
        // balance must land exactly on zero - never negative.
        seedStock(businessId, productId, "2", "60.0000");

        long saleA = draftProductSale(token, productId);
        long saleB = draftProductSale(token, productId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<Integer> first = pool.submit(() -> complete(token, saleA, ready, go));
            Future<Integer> second = pool.submit(() -> complete(token, saleB, ready, go));
            ready.await();
            go.countDown();
            int statusA = first.get();
            int statusB = second.get();
            assertThat(statusA).as("first completion status").isEqualTo(200);
            assertThat(statusB).as("second completion status").isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }

        String bodyA = getSaleBody(token, saleA);
        String bodyB = getSaleBody(token, saleB);
        String numberA = extractSaleNumber(bodyA);
        String numberB = extractSaleNumber(bodyB);
        assertThat(numberA).isNotEqualTo(numberB);
        Set<String> numbers = new HashSet<>();
        numbers.add(numberA);
        numbers.add(numberB);
        assertThat(numbers).containsExactlyInAnyOrder("1", "2");

        ProductStockBalance balance = balanceRepository.findByBusinessIdAndProductId(businessId, productId).orElseThrow();
        assertThat(balance.getQuantityOnHand().compareTo(BigDecimal.ZERO))
                .as("two units sold of two on hand - zero exactly, never negative").isZero();
    }

    private int complete(String token, long saleId, CountDownLatch ready, CountDownLatch go) {
        try {
            ready.countDown();
            go.await();
            MvcResult result = mockMvc.perform(post("/api/v1/sales/" + saleId + "/complete")
                            .header("Authorization", "Bearer " + token))
                    .andReturn();
            return result.getResponse().getStatus();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getSaleBody(String token, long saleId) throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/sales/" + saleId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private String extractSaleNumber(String body) {
        int index = body.indexOf("\"saleNumber\":\"");
        int start = index + 14;
        int end = body.indexOf("\"", start);
        return body.substring(start, end);
    }

    private long draftProductSale(String token, long productId) throws Exception {
        MvcResult sale = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemType\":\"PRODUCT\",\"productId\":" + productId + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        long saleId = extractId(sale.getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/sales/" + saleId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"amount\":150.0000"
                                + ",\"idempotencyKey\":\"race-" + saleId + "\"}"))
                .andExpect(status().isCreated());
        return saleId;
    }

    private void seedStock(long businessId, long productId, String quantity, String averageCost) {
        // Product creation now inserts the zero balance row (blueprint 3.6), so
        // seeding stock means UPDATING that row - inserting would violate the
        // one-balance-per-product unique constraint.
        ProductStockBalance balance = balanceRepository
                .findByBusinessIdAndProductId(businessId, productId)
                .orElseGet(ProductStockBalance::new);
        balance.setBusinessId(businessId);
        balance.setProductId(productId);
        balance.setQuantityOnHand(new BigDecimal(quantity));
        balance.setAverageUnitCost(new BigDecimal(averageCost));
        balanceRepository.save(balance);
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
