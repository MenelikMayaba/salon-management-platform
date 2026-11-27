package za.co.salon.sales;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5 tests #5, #7, #9: draft creation with catalogue snapshots, item-shape
 * validation, cross-tenant rejection. Written before implementation (RGR).
 *
 * Documented pricing decision (open §12 question): catalogue prices are treated
 * as TAX-EXCLUSIVE - tax_amount is added on top at rate/100. Salons that quote
 * VAT-inclusive prices would need the inverse calculation; that policy decision
 * is deliberately not smuggled into this phase.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SaleDraftIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void draftCapturesSnapshotsAndComputesTotals() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("draft.basic"));
        api.createBusiness(token, "Draft Salon");
        long categoryId = createServiceCategory(token);
        long serviceId = createService(token, categoryId, "Haircut", "250.0000");

        MvcResult created = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemType\":\"SERVICE\",\"serviceId\":" + serviceId
                                + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        assertThat(body).contains("\"status\":\"DRAFT\"");
        assertThat(body).contains("250.0000");
        assertThat(body).contains("Haircut");
        // Drafts never burn sale numbers - allocated at completion only.
        assertThat(body).contains("\"saleNumber\":null");
    }

    @Test
    void snapshotSurvivesLaterPriceChange() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("draft.snap"));
        api.createBusiness(token, "Snap Salon");
        long categoryId = createServiceCategory(token);
        long serviceId = createService(token, categoryId, "Cut", "250.0000");

        MvcResult created = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemType\":\"SERVICE\",\"serviceId\":" + serviceId
                                + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        long saleId = extractId(created.getResponse().getContentAsString());

        // Price changes AFTER the draft.
        mockMvc.perform(put("/api/v1/services/" + serviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId
                                + ",\"name\":\"Cut\",\"durationMinutes\":30,\"price\":300.0000,\"active\":true}"))
                .andExpect(status().isOk());

        // The draft receipt keeps the old price - snapshot, never re-read.
        MvcResult detail = mockMvc.perform(get("/api/v1/sales/" + saleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(detail.getResponse().getContentAsString()).contains("250.0000");
        assertThat(detail.getResponse().getContentAsString()).doesNotContain("300.0000");
    }

    @Test
    void taxIsSnapshottedPerLine() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("draft.tax"));
        api.createBusiness(token, "Tax Salon");
        long categoryId = createServiceCategory(token);
        long taxCodeId = createTaxCode(token);
        // Service with VAT 15%: tax-exclusive price 100 -> tax 15, line total 115.
        long serviceId = createServiceWithTax(token, categoryId, taxCodeId);

        MvcResult created = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemType\":\"SERVICE\",\"serviceId\":" + serviceId
                                + ",\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        assertThat(body).contains("\"taxAmount\":30.0000");
        assertThat(body).contains("\"totalAmount\":230.0000");
    }

    @Test
    void itemShapeViolationsAreRejected() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("draft.shape"));
        api.createBusiness(token, "Shape Salon");
        long categoryId = createServiceCategory(token);
        long serviceId = createService(token, categoryId, "Cut", "100.0000");
        long productCategoryId = createProductCategory(token);
        long productId = createProduct(token, productCategoryId);

        // Both service and product on one item.
        assertRejected(token, "{\"items\":[{\"itemType\":\"SERVICE\",\"serviceId\":" + serviceId
                + ",\"productId\":" + productId + ",\"quantity\":1}]}");
        // Neither.
        assertRejected(token, "{\"items\":[{\"itemType\":\"SERVICE\",\"quantity\":1}]}");
        // Type mismatch: product id on a SERVICE item.
        assertRejected(token, "{\"items\":[{\"itemType\":\"SERVICE\",\"productId\":" + productId
                + ",\"quantity\":1}]}");
    }

    @Test
    void crossTenantCatalogueItemIsRejectedAtDraft() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String tokenA = api.registerUser(TestApiHelper.uniqueEmail("draft.xt.a"));
        api.createBusiness(tokenA, "XT A");
        long categoryA = createServiceCategory(tokenA);
        long foreignServiceId = createService(tokenA, categoryA, "A-Cut", "100.0000");

        String tokenB = api.registerUser(TestApiHelper.uniqueEmail("draft.xt.b"));
        api.createBusiness(tokenB, "XT B");

        MvcResult result = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemType\":\"SERVICE\",\"serviceId\":" + foreignServiceId
                                + ",\"quantity\":1}]}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isIn(404, 422);
    }

    private void assertRejected(String token, String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("shape violation must be rejected, got " + result.getResponse().getStatus())
                .isIn(400, 422);
    }

    private long createServiceCategory(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/service-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hair\"}"))
                .andExpect(status().isCreated()).andReturn();
        return extractId(result.getResponse().getContentAsString());
    }

    private long createService(String token, long categoryId, String name, String price) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"" + name
                                + "\",\"durationMinutes\":30,\"price\":" + price + "}"))
                .andExpect(status().isCreated()).andReturn();
        return extractId(result.getResponse().getContentAsString());
    }

    private long createServiceWithTax(String token, long categoryId, long taxCodeId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"Taxed\",\"durationMinutes\":30"
                                + ",\"price\":100.0000,\"taxCodeId\":" + taxCodeId + "}"))
                .andExpect(status().isCreated()).andReturn();
        return extractId(result.getResponse().getContentAsString());
    }

    private long createTaxCode(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tax-codes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"VAT\",\"rate\":15.0000,\"taxType\":\"VAT\"}"))
                .andExpect(status().isCreated()).andReturn();
        return extractId(result.getResponse().getContentAsString());
    }

    private long createProductCategory(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/product-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Retail\"}"))
                .andExpect(status().isCreated()).andReturn();
        return extractId(result.getResponse().getContentAsString());
    }

    private long createProduct(String token, long categoryId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"Shampoo\",\"sellingPrice\":50.0000}"))
                .andExpect(status().isCreated()).andReturn();
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
