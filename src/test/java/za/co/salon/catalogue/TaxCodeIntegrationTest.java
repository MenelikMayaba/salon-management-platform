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
 * Slice 1 spec: tax_codes CRUD, tenant isolation, role gates, decimal scale,
 * constant query count on the paged list. Written before the implementation (RGR).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaxCodeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Test
    void crudRoundTrip() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("tax.owner"));
        api.createBusiness(token, "Tax Salon");

        MvcResult created = mockMvc.perform(post("/api/v1/tax-codes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"VAT 15%\",\"rate\":15.0000,\"taxType\":\"VAT\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        assertThat(body).contains("\"name\":\"VAT 15%\"");
        long id = extractId(body);
        assertThat(id).isPositive();

        MvcResult list = mockMvc.perform(get("/api/v1/tax-codes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(list.getResponse().getContentAsString()).contains("VAT 15%");

        MvcResult updated = mockMvc.perform(put("/api/v1/tax-codes/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"VAT 15% updated\",\"rate\":15.0000,\"taxType\":\"VAT\",\"active\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(updated.getResponse().getContentAsString()).contains("VAT 15% updated");
    }

    @Test
    void decimalScaleIsPreserved() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("tax.scale"));
        api.createBusiness(token, "Scale Salon");

        MvcResult created = mockMvc.perform(post("/api/v1/tax-codes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"VAT\",\"rate\":15.0000,\"taxType\":\"VAT\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        // DECIMAL(7,4) must round-trip exactly - string comparison of the JSON body
        // catches silent float conversion or scale loss.
        assertThat(created.getResponse().getContentAsString()).contains("15.0000");
    }

    @Test
    void roleGatesAreEnforced() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String ownerToken = api.registerUser(TestApiHelper.uniqueEmail("tax.role.owner"));
        api.createBusiness(ownerToken, "Role Salon");
        String receptionistEmail = TestApiHelper.uniqueEmail("tax.role.rec");
        String receptionistToken = api.registerUser(receptionistEmail);
        api.addMember(ownerToken, receptionistEmail, "RECEPTIONIST");
        String managerEmail = TestApiHelper.uniqueEmail("tax.role.mgr");
        String managerToken = api.registerUser(managerEmail);
        api.addMember(ownerToken, managerEmail, "MANAGER");

        String body = "{\"name\":\"VAT\",\"rate\":15.0000,\"taxType\":\"VAT\"}";
        // Receptionist cannot write.
        mockMvc.perform(post("/api/v1/tax-codes")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        // Manager can.
        mockMvc.perform(post("/api/v1/tax-codes")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void tenantIsolationOnListAndUpdate() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String tokenA = api.registerUser(TestApiHelper.uniqueEmail("tax.a"));
        api.createBusiness(tokenA, "Salon A");
        String tokenB = api.registerUser(TestApiHelper.uniqueEmail("tax.b"));
        api.createBusiness(tokenB, "Salon B");

        MvcResult created = mockMvc.perform(post("/api/v1/tax-codes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A-VAT\",\"rate\":15.0000,\"taxType\":\"VAT\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long aId = extractId(created.getResponse().getContentAsString());

        // B's list must not contain A's tax code.
        MvcResult bList = mockMvc.perform(get("/api/v1/tax-codes")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(bList.getResponse().getContentAsString()).doesNotContain("A-VAT");

        // B updating A's tax code by id is rejected (404 or 403 - never a success).
        MvcResult result = mockMvc.perform(put("/api/v1/tax-codes/" + aId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked\",\"rate\":1.0,\"taxType\":\"VAT\",\"active\":true}"))
                .andReturn();
        int status = result.getResponse().getStatus();
        assertThat(status).as("cross-tenant update must be 403 or 404, was " + status)
                .isIn(403, 404);
    }

    @Test
    void listQueryCountDoesNotScaleWithRows() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("tax.qc"));
        api.createBusiness(token, "QC Salon");

        createTaxCode(token, "QC1");
        long countSmall = countStatementsDuringList(token);

        for (int index = 2; index <= 5; index++) {
            createTaxCode(token, "QC" + index);
        }
        long countLarge = countStatementsDuringList(token);

        assertThat(countLarge)
                .as("query count must not grow with rows (small=%d, large=%d)", countSmall, countLarge)
                .isEqualTo(countSmall);
    }

    private void createTaxCode(String token, String name) throws Exception {
        mockMvc.perform(post("/api/v1/tax-codes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"rate\":15.0000,\"taxType\":\"VAT\"}"));
    }

    private long countStatementsDuringList(String token) throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long before = statistics.getPrepareStatementCount();
        mockMvc.perform(get("/api/v1/tax-codes")
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
