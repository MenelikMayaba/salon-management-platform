package za.co.salon.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import za.co.salon.inventory.entity.ProductStockBalance;
import za.co.salon.inventory.repository.ProductStockBalanceRepository;
import za.co.salon.support.TestApiHelper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 6 tests #6 and #7: stock adjustments and damage write signed movements
 * at the current average, negative stock is rejected, the low-stock flag and
 * the movements audit read path are correct. Written before implementation (RGR).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InventoryReadAndAdjustmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductStockBalanceRepository balanceRepository;

    @Test
    void adjustmentsWriteSignedMovementsAtCurrentAverage() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("inv.adjust"));
        long businessId = api.createBusiness(token, "Adjustment Salon");
        long productId = createProduct(token, "10.0000");
        seedStock(businessId, productId, "5", "60.0000");

        // +3 STOCK_ADJUSTMENT (found stock in the back room).
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantityChange\":3"
                                + ",\"movementType\":\"STOCK_ADJUSTMENT\",\"reason\":\"Found in storage\"}"))
                .andExpect(status().isCreated());
        ProductStockBalance afterPlus = balanceRepository.findByBusinessIdAndProductId(businessId, productId).orElseThrow();
        assertThat(afterPlus.getQuantityOnHand().compareTo(new BigDecimal("8.0000"))).isZero();

        // -2 DAMAGE (dropped bottles).
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantityChange\":-2"
                                + ",\"movementType\":\"DAMAGE\",\"reason\":\"Dropped\"}"))
                .andExpect(status().isCreated());
        ProductStockBalance afterDamage = balanceRepository.findByBusinessIdAndProductId(businessId, productId).orElseThrow();
        assertThat(afterDamage.getQuantityOnHand().compareTo(new BigDecimal("6.0000"))).isZero();
        assertThat(afterDamage.getAverageUnitCost().compareTo(new BigDecimal("60.0000")))
                .as("adjustments never change the average").isZero();

        // Movements audit trail: both rows present, correctly signed, at current average.
        MvcResult movements = mockMvc.perform(get("/api/v1/inventory/" + productId + "/movements")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String body = movements.getResponse().getContentAsString();
        assertThat(body).contains("STOCK_ADJUSTMENT");
        assertThat(body).contains("DAMAGE");
        assertThat(body).contains("Found in storage");
        assertThat(body).contains("60.0000");
    }

    @Test
    void negativeStockAdjustmentRejected() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("inv.negedge"));
        long businessId = api.createBusiness(token, "Negative Salon");
        long productId = createProduct(token, "10.0000");
        seedStock(businessId, productId, "2", "60.0000");

        MvcResult result = mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantityChange\":-5"
                                + ",\"movementType\":\"DAMAGE\",\"reason\":\"More than we have\"}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isIn(409, 422);
        ProductStockBalance balance = balanceRepository.findByBusinessIdAndProductId(businessId, productId).orElseThrow();
        assertThat(balance.getQuantityOnHand().compareTo(new BigDecimal("2.0000")))
                .as("rejected adjustment must not move stock").isZero();
    }

    @Test
    void lowStockFlaggedInInventoryList() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("inv.lowstock"));
        long businessId = api.createBusiness(token, "Low Stock Salon");
        long productId = createProductWithReorderLevel(token, "5.0000");
        seedStock(businessId, productId, "3.0000", "60.0000");

        MvcResult list = mockMvc.perform(get("/api/v1/inventory")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String body = list.getResponse().getContentAsString();
        // 3 on hand against a reorder level of 5: must be flagged.
        assertThat(body).contains("\"lowStock\":true");
        assertThat(body).contains("\"quantityOnHand\":3.0000");
    }

    private void seedStock(long businessId, long productId, String quantity, String averageCost) {
        ProductStockBalance balance = balanceRepository
                .findByBusinessIdAndProductId(businessId, productId)
                .orElseThrow(() -> new AssertionError("product creation must insert the balance row"));
        balance.setQuantityOnHand(new BigDecimal(quantity));
        balance.setAverageUnitCost(new BigDecimal(averageCost));
        balanceRepository.save(balance);
    }

    private long createProduct(String token, String price) throws Exception {
        return createProduct(token, price, null);
    }

    private long createProductWithReorderLevel(String token, String reorderLevel) throws Exception {
        return createProduct(token, "10.0000", reorderLevel);
    }

    private long createProduct(String token, String price, String reorderLevel) throws Exception {
        MvcResult category = mockMvc.perform(post("/api/v1/product-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Retail\"}"))
                .andReturn();
        long categoryId = extractId(category.getResponse().getContentAsString());
        String reorder = reorderLevel == null ? "" : ",\"reorderLevel\":" + reorderLevel;
        MvcResult product = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"Shampoo\",\"sellingPrice\":"
                                + price + reorder + "}"))
                .andExpect(status().isCreated())
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
