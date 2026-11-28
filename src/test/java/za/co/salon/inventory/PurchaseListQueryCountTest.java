package za.co.salon.inventory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.stat.Statistics;
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

/**
 * N+1 regression guard for GET /api/v1/purchases (blueprint rule 15). Items and
 * suppliers must be batch-fetched for the page: the SQL statement count must be
 * identical for 1 purchase and for 4. A count that grows with rows means a
 * repository call inside the mapping loop - the exact bug this test guards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PurchaseListQueryCountTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Test
    void queryCountDoesNotScaleWithPurchaseCount() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("qc.purchases"));
        api.createBusiness(token, "Purchase QC Salon");
        long supplierId = createSupplier(token);
        long productId = createProduct(token);

        createPurchase(token, supplierId, productId);
        long countSmall = countStatementsDuringList(token);

        for (int index = 0; index < 3; index++) {
            createPurchase(token, supplierId, productId);
        }
        long countLarge = countStatementsDuringList(token);

        assertThat(countLarge)
                .as("SQL statement count must not grow with purchase count (small=%d, large=%d)", countSmall, countLarge)
                .isEqualTo(countSmall);
    }

    private long countStatementsDuringList(String token) throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        long before = statistics.getPrepareStatementCount();
        mockMvc.perform(get("/api/v1/purchases").header("Authorization", "Bearer " + token));
        return statistics.getPrepareStatementCount() - before;
    }

    private long createSupplier(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"QC Supplier\"}"))
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
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"QC Product\",\"sellingPrice\":10.0000}"))
                .andReturn();
        return extractId(product.getResponse().getContentAsString());
    }

    private void createPurchase(String token, long supplierId, long productId) throws Exception {
        mockMvc.perform(post("/api/v1/purchases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"purchaseDate\":\"2026-11-28\""
                                + ",\"items\":[{\"productId\":" + productId + ",\"quantity\":1,\"unitCost\":10.0000}]}"))
                .andReturn();
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
