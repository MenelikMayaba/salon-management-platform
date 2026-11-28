package za.co.salon.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import za.co.salon.support.TestApiHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Purchases: draft creation with items and computed totals, cross-tenant
 * supplier/product rejection (phase 6 test #8). Written before implementation (RGR).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PurchaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createDraftComputesTotalsAndPersistsItems() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("purchase.create"));
        api.createBusiness(token, "Purchase Salon");
        long supplierId = createSupplier(token, "BeautyWholesale");
        long productId = createProduct(token, "Shampoo");

        MvcResult created = mockMvc.perform(post("/api/v1/purchases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"invoiceNumber\":\"INV-100\""
                                + ",\"purchaseDate\":\"2026-11-28\""
                                + ",\"items\":[{\"productId\":" + productId + ",\"quantity\":10,\"unitCost\":50.0000}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        assertThat(body).contains("\"status\":\"DRAFT\"");
        assertThat(body).contains("\"subtotal\":500.0000");
        assertThat(body).contains("\"totalAmount\":500.0000");
        assertThat(body).contains("INV-100");
        assertThat(body).contains("\"items\":[");
        assertThat(body).contains("\"quantity\":10");
    }

    @Test
    void crossTenantSupplierRejected() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String tokenA = api.registerUser(TestApiHelper.uniqueEmail("purchase.xt.a"));
        api.createBusiness(tokenA, "Purchase Salon A");
        long foreignSupplierId = createSupplier(tokenA, "A-Supplier");

        String tokenB = api.registerUser(TestApiHelper.uniqueEmail("purchase.xt.b"));
        api.createBusiness(tokenB, "Purchase Salon B");
        long productId = createProduct(tokenB, "Shampoo");

        MvcResult result = mockMvc.perform(post("/api/v1/purchases")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + foreignSupplierId + ",\"purchaseDate\":\"2026-11-28\""
                                + ",\"items\":[{\"productId\":" + productId + ",\"quantity\":1,\"unitCost\":10.0000}]}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isIn(404, 422);
    }

    @Test
    void crossTenantProductInItemsRejected() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String tokenA = api.registerUser(TestApiHelper.uniqueEmail("purchase.xtp.a"));
        api.createBusiness(tokenA, "Purchase Salon C");
        long foreignProductId = createProduct(tokenA, "A-Product");

        String tokenB = api.registerUser(TestApiHelper.uniqueEmail("purchase.xtp.b"));
        api.createBusiness(tokenB, "Purchase Salon D");
        long supplierId = createSupplier(tokenB, "B-Supplier");

        MvcResult result = mockMvc.perform(post("/api/v1/purchases")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"purchaseDate\":\"2026-11-28\""
                                + ",\"items\":[{\"productId\":" + foreignProductId + ",\"quantity\":1,\"unitCost\":10.0000}]}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isIn(404, 422);
    }

    @Test
    void receptionistCannotCreatePurchases() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String ownerToken = api.registerUser(TestApiHelper.uniqueEmail("purchase.gate.owner"));
        api.createBusiness(ownerToken, "Purchase Gate Salon");
        String receptionistEmail = TestApiHelper.uniqueEmail("purchase.gate.recep");
        String receptionistToken = api.registerUser(receptionistEmail);
        api.addMember(ownerToken, receptionistEmail, "RECEPTIONIST");

        MvcResult result = mockMvc.perform(post("/api/v1/purchases")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":1,\"purchaseDate\":\"2026-11-28\""
                                + ",\"items\":[{\"productId\":1,\"quantity\":1,\"unitCost\":10}]}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isIn(403, 401);
    }

    long createSupplier(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(result.getResponse().getContentAsString());
    }

    long createProduct(String token, String name) throws Exception {
        MvcResult category = mockMvc.perform(post("/api/v1/product-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Retail\"}"))
                .andReturn();
        long categoryId = extractId(category.getResponse().getContentAsString());
        MvcResult product = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"" + name
                                + "\",\"sellingPrice\":150.0000}"))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(product.getResponse().getContentAsString());
    }

    long extractId(String json) {
        int index = json.indexOf("\"id\":");
        int start = index + 5;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }
}
