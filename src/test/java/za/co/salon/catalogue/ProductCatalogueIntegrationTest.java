package za.co.salon.catalogue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice 3 spec: product_categories + products. Written before implementation (RGR).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductCatalogueIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Test
    void productCrudRoundTripPreservesDecimalScale() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("prod.crud"));
        api.createBusiness(token, "Product Salon");
        long categoryId = createCategory(token, "Retail");

        MvcResult created = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId
                                + ",\"name\":\"Shampoo\",\"sku\":\"SH-001\",\"barcode\":\"6001234567890\""
                                + ",\"sellingPrice\":99.9900,\"reorderLevel\":5.0000,\"trackInventory\":true}"))
                .andExpect(status().isCreated())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        assertThat(body).contains("99.9900");
        assertThat(body).contains("SH-001");

        MvcResult list = mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(list.getResponse().getContentAsString()).contains("\"categoryName\":\"Retail\"");
    }

    @Test
    void crossTenantCategoryAttachIsRejected() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String tokenA = api.registerUser(TestApiHelper.uniqueEmail("prod.xt.a"));
        api.createBusiness(tokenA, "Salon PA");
        long foreignCategoryId = createCategory(tokenA, "A-Cat");

        String tokenB = api.registerUser(TestApiHelper.uniqueEmail("prod.xt.b"));
        api.createBusiness(tokenB, "Salon PB");

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + foreignCategoryId
                                + ",\"name\":\"Hijack\",\"sellingPrice\":50.0000}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isIn(404, 422);
    }

    @Test
    void tenantIsolationOnListAndUpdate() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String tokenA = api.registerUser(TestApiHelper.uniqueEmail("prod.iso.a"));
        api.createBusiness(tokenA, "Iso PA");
        long categoryA = createCategory(tokenA, "IsoCat");
        MvcResult created = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryA
                                + ",\"name\":\"A-Product\",\"sellingPrice\":50.0000}"))
                .andExpect(status().isCreated())
                .andReturn();
        long productId = extractId(created.getResponse().getContentAsString());

        String tokenB = api.registerUser(TestApiHelper.uniqueEmail("prod.iso.b"));
        api.createBusiness(tokenB, "Iso PB");

        MvcResult bList = mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(bList.getResponse().getContentAsString()).doesNotContain("A-Product");

        MvcResult result = mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryA
                                + ",\"name\":\"Hijacked\",\"sellingPrice\":1.0,\"active\":true}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isIn(403, 404);
    }

    @Test
    void roleGatesAreEnforced() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String ownerToken = api.registerUser(TestApiHelper.uniqueEmail("prod.role.owner"));
        api.createBusiness(ownerToken, "Role Salon");
        long categoryId = createCategory(ownerToken, "RoleCat");
        String receptionistEmail = TestApiHelper.uniqueEmail("prod.role.rec");
        String receptionistToken = api.registerUser(receptionistEmail);
        api.addMember(ownerToken, receptionistEmail, "RECEPTIONIST");

        String body = "{\"categoryId\":" + categoryId + ",\"name\":\"P\",\"sellingPrice\":10.0000}";
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + receptionistToken))
                .andExpect(status().isOk());
    }

    @Test
    void listQueryCountDoesNotScaleWithRows() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("prod.qc"));
        api.createBusiness(token, "QC Salon");
        long categoryId = createCategory(token, "QCCat");

        createProduct(token, categoryId, "QC1");
        long countSmall = countStatementsDuringList(token);

        for (int index = 2; index <= 5; index++) {
            createProduct(token, categoryId, "QC" + index);
        }
        long countLarge = countStatementsDuringList(token);

        assertThat(countLarge)
                .as("query count must not grow with rows (small=%d, large=%d)", countSmall, countLarge)
                .isEqualTo(countSmall);
    }

    private long createCategory(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/product-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(result.getResponse().getContentAsString());
    }

    private void createProduct(String token, long categoryId, String name) throws Exception {
        mockMvc.perform(post("/api/v1/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":" + categoryId
                        + ",\"name\":\"" + name + "\",\"sellingPrice\":10.0000}"));
    }

    private long countStatementsDuringList(String token) throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long before = statistics.getPrepareStatementCount();
        mockMvc.perform(get("/api/v1/products")
                .header("Authorization", "Bearer " + token));
        return statistics.getPrepareStatementCount() - before;
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
