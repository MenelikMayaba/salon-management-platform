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
 * Slice 2 spec: service_categories + services. Written before implementation (RGR).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ServiceCatalogueIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Test
    void categoryCrudRoundTrip() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("svc.cat"));
        api.createBusiness(token, "Cat Salon");

        MvcResult created = mockMvc.perform(post("/api/v1/service-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hair\",\"description\":\"Hair services\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long id = extractId(created.getResponse().getContentAsString());

        MvcResult updated = mockMvc.perform(put("/api/v1/service-categories/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hair & Beauty\",\"description\":\"Updated\",\"active\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(updated.getResponse().getContentAsString()).contains("Hair & Beauty");
    }

    @Test
    void serviceCrudRoundTripPreservesDecimalScale() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("svc.crud"));
        api.createBusiness(token, "Service Salon");
        long categoryId = createCategory(token, "Hair");

        MvcResult created = mockMvc.perform(post("/api/v1/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId
                                + ",\"name\":\"Haircut\",\"durationMinutes\":45,\"price\":250.0000}"))
                .andExpect(status().isCreated())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        assertThat(body).contains("250.0000");
        assertThat(body).contains("\"durationMinutes\":45");

        // The paged list must expose the resolved category name (batch-fetched, not per-row).
        MvcResult list = mockMvc.perform(get("/api/v1/services")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String listBody = list.getResponse().getContentAsString();
        assertThat(listBody).contains("Haircut");
        assertThat(listBody).contains("\"categoryName\":\"Hair\"");
    }

    @Test
    void crossTenantCategoryAttachIsRejected() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String tokenA = api.registerUser(TestApiHelper.uniqueEmail("svc.xt.a"));
        api.createBusiness(tokenA, "Salon A");
        long foreignCategoryId = createCategory(tokenA, "A-Cat");

        String tokenB = api.registerUser(TestApiHelper.uniqueEmail("svc.xt.b"));
        api.createBusiness(tokenB, "Salon B");

        MvcResult result = mockMvc.perform(post("/api/v1/services")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + foreignCategoryId
                                + ",\"name\":\"Hijack\",\"durationMinutes\":30,\"price\":100.0000}"))
                .andReturn();
        int status = result.getResponse().getStatus();
        assertThat(status).as("cross-tenant category attach must be 404 or 422, was " + status)
                .isIn(404, 422);
    }

    @Test
    void crossTenantTaxCodeAttachIsRejected() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String tokenA = api.registerUser(TestApiHelper.uniqueEmail("svc.xt.tax.a"));
        api.createBusiness(tokenA, "Salon TA");
        MvcResult tax = mockMvc.perform(post("/api/v1/tax-codes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A-VAT\",\"rate\":15.0000,\"taxType\":\"VAT\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long foreignTaxId = extractId(tax.getResponse().getContentAsString());

        String tokenB = api.registerUser(TestApiHelper.uniqueEmail("svc.xt.tax.b"));
        api.createBusiness(tokenB, "Salon TB");
        long categoryId = createCategory(tokenB, "B-Cat");

        MvcResult result = mockMvc.perform(post("/api/v1/services")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId
                                + ",\"name\":\"Hijack\",\"durationMinutes\":30,\"price\":100.0000"
                                + ",\"taxCodeId\":" + foreignTaxId + "}"))
                .andReturn();
        int status = result.getResponse().getStatus();
        assertThat(status).as("cross-tenant tax code attach must be 404 or 422, was " + status)
                .isIn(404, 422);
    }

    @Test
    void tenantIsolationOnListAndUpdate() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String tokenA = api.registerUser(TestApiHelper.uniqueEmail("svc.iso.a"));
        api.createBusiness(tokenA, "Iso A");
        long categoryA = createCategory(tokenA, "IsoCat");
        MvcResult created = mockMvc.perform(post("/api/v1/services")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryA
                                + ",\"name\":\"A-Service\",\"durationMinutes\":30,\"price\":100.0000}"))
                .andExpect(status().isCreated())
                .andReturn();
        long serviceId = extractId(created.getResponse().getContentAsString());

        String tokenB = api.registerUser(TestApiHelper.uniqueEmail("svc.iso.b"));
        api.createBusiness(tokenB, "Iso B");

        MvcResult bList = mockMvc.perform(get("/api/v1/services")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(bList.getResponse().getContentAsString()).doesNotContain("A-Service");

        MvcResult result = mockMvc.perform(put("/api/v1/services/" + serviceId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryA
                                + ",\"name\":\"Hijacked\",\"durationMinutes\":30,\"price\":1.0,\"active\":true}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isIn(403, 404);
    }

    @Test
    void roleGatesAreEnforced() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String ownerToken = api.registerUser(TestApiHelper.uniqueEmail("svc.role.owner"));
        api.createBusiness(ownerToken, "Role Salon");
        long categoryId = createCategory(ownerToken, "RoleCat");
        String receptionistEmail = TestApiHelper.uniqueEmail("svc.role.rec");
        String receptionistToken = api.registerUser(receptionistEmail);
        api.addMember(ownerToken, receptionistEmail, "RECEPTIONIST");

        String body = "{\"categoryId\":" + categoryId
                + ",\"name\":\"Cut\",\"durationMinutes\":30,\"price\":100.0000}";
        mockMvc.perform(post("/api/v1/services")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        // Any active member may READ the catalogue.
        mockMvc.perform(get("/api/v1/services")
                        .header("Authorization", "Bearer " + receptionistToken))
                .andExpect(status().isOk());
    }

    @Test
    void listQueryCountDoesNotScaleWithRows() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("svc.qc"));
        api.createBusiness(token, "QC Salon");
        long categoryId = createCategory(token, "QCCat");

        createService(token, categoryId, "QC1");
        long countSmall = countStatementsDuringList(token);

        for (int index = 2; index <= 5; index++) {
            createService(token, categoryId, "QC" + index);
        }
        long countLarge = countStatementsDuringList(token);

        assertThat(countLarge)
                .as("query count must not grow with rows (small=%d, large=%d)", countSmall, countLarge)
                .isEqualTo(countSmall);
    }

    private long createCategory(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/service-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(result.getResponse().getContentAsString());
    }

    private void createService(String token, long categoryId, String name) throws Exception {
        mockMvc.perform(post("/api/v1/services")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":" + categoryId
                        + ",\"name\":\"" + name + "\",\"durationMinutes\":30,\"price\":100.0000}"));
    }

    private long countStatementsDuringList(String token) throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long before = statistics.getPrepareStatementCount();
        mockMvc.perform(get("/api/v1/services")
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
