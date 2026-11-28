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
import za.co.salon.inventory.repository.InventoryMovementRepository;
import za.co.salon.inventory.repository.ProductStockBalanceRepository;
import za.co.salon.support.TestApiHelper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 6 slice 0: the blueprint 3.6 foundation invariants. A product creation
 * must insert its zero stock-balance row (so FOR UPDATE locks attach to a row,
 * never a gap), and a non-tracked product must sell without any stock movement.
 * Written before implementation (RGR).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InventoryFoundationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductStockBalanceRepository balanceRepository;

    @Autowired
    private InventoryMovementRepository movementRepository;

    @Test
    void productCreationInsertsZeroBalanceRow() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("inv.foundation"));
        long businessId = api.createBusiness(token, "Foundation Salon");
        long productId = createProduct(token, "Shampoo", true);

        ProductStockBalance balance = balanceRepository.findByBusinessIdAndProductId(businessId, productId)
                .orElseThrow(() -> new AssertionError("product creation must insert the zero balance row"));
        assertThat(balance.getQuantityOnHand().compareTo(BigDecimal.ZERO)).isZero();
        assertThat(balance.getAverageUnitCost().compareTo(BigDecimal.ZERO)).isZero();
    }

    @Test
    void nonTrackedProductSellsWithoutMovementsOrDeduction() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("inv.nontracked"));
        long businessId = api.createBusiness(token, "Nontracked Salon");
        long productId = createProduct(token, "Disposable cape", false);

        long saleId = draftProductSale(token, productId);
        mockMvc.perform(post("/api/v1/sales/" + saleId + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // A non-tracked product never errors for missing stock and never writes movements.
        assertThat(movementRepository.findAllByReferenceTypeAndReferenceId("SALE", saleId)).isEmpty();
        ProductStockBalance balance = balanceRepository.findByBusinessIdAndProductId(businessId, productId).orElseThrow();
        assertThat(balance.getQuantityOnHand().compareTo(BigDecimal.ZERO))
                .as("non-tracked stock is never decremented").isZero();
    }

    @Test
    void trackedProductStillDeducts() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("inv.tracked"));
        long businessId = api.createBusiness(token, "Tracked Salon");
        long productId = createProduct(token, "Shampoo", true);

        ProductStockBalance balance = balanceRepository.findByBusinessIdAndProductId(businessId, productId).orElseThrow();
        balance.setQuantityOnHand(new BigDecimal("5"));
        balance.setAverageUnitCost(new BigDecimal("60.0000"));
        balanceRepository.save(balance);

        long saleId = draftProductSale(token, productId);
        mockMvc.perform(post("/api/v1/sales/" + saleId + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(movementRepository.findAllByReferenceTypeAndReferenceId("SALE", saleId)).hasSize(1);
        ProductStockBalance after = balanceRepository.findByBusinessIdAndProductId(businessId, productId).orElseThrow();
        assertThat(after.getQuantityOnHand().compareTo(new BigDecimal("4"))).isZero();
    }

    private long createProduct(String token, String name, boolean trackInventory) throws Exception {
        MvcResult category = mockMvc.perform(post("/api/v1/product-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Retail\"}"))
                .andReturn();
        long categoryId = extractId(category.getResponse().getContentAsString());
        MvcResult product = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"" + name
                                + "\",\"sellingPrice\":150.0000,\"trackInventory\":" + trackInventory + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(product.getResponse().getContentAsString());
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
