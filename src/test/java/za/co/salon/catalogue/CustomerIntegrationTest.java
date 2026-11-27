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
 * Slice 4 spec: customers. Written before implementation (RGR).
 * Customer writes are allowed for RECEPTIONIST per the 7.4 matrix.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Test
    void crudRoundTrip() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("cust.crud"));
        api.createBusiness(token, "Customer Salon");

        MvcResult created = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Naledi\",\"lastName\":\"Mokoena\""
                                + ",\"email\":\"naledi@example.com\",\"phoneNumber\":\"0821234567\""
                                + ",\"notes\":\"Prefers afternoon appointments\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        assertThat(body).contains("Naledi");
        long id = extractId(body);

        MvcResult fetched = mockMvc.perform(get("/api/v1/customers/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(fetched.getResponse().getContentAsString()).contains("Prefers afternoon");

        MvcResult updated = mockMvc.perform(put("/api/v1/customers/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Naledi\",\"lastName\":\"Mokoena-Updated\""
                                + ",\"active\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(updated.getResponse().getContentAsString()).contains("Mokoena-Updated");
    }

    @Test
    void tenantIsolationOnListGetAndUpdate() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String tokenA = api.registerUser(TestApiHelper.uniqueEmail("cust.iso.a"));
        api.createBusiness(tokenA, "Iso A");
        MvcResult created = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"TenantA\",\"lastName\":\"Customer\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long customerId = extractId(created.getResponse().getContentAsString());

        String tokenB = api.registerUser(TestApiHelper.uniqueEmail("cust.iso.b"));
        api.createBusiness(tokenB, "Iso B");

        MvcResult bList = mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(bList.getResponse().getContentAsString()).doesNotContain("TenantA");

        MvcResult bGet = mockMvc.perform(get("/api/v1/customers/" + customerId)
                        .header("Authorization", "Bearer " + tokenB))
                .andReturn();
        assertThat(bGet.getResponse().getStatus()).isIn(403, 404);

        MvcResult bUpdate = mockMvc.perform(put("/api/v1/customers/" + customerId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Hijack\",\"lastName\":\"X\",\"active\":true}"))
                .andReturn();
        assertThat(bUpdate.getResponse().getStatus()).isIn(403, 404);
    }

    @Test
    void receptionistCanWriteButStylistCannot() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String ownerToken = api.registerUser(TestApiHelper.uniqueEmail("cust.role.owner"));
        api.createBusiness(ownerToken, "Role Salon");
        String receptionistEmail = TestApiHelper.uniqueEmail("cust.role.rec");
        String receptionistToken = api.registerUser(receptionistEmail);
        api.addMember(ownerToken, receptionistEmail, "RECEPTIONIST");
        String stylistEmail = TestApiHelper.uniqueEmail("cust.role.sty");
        String stylistToken = api.registerUser(stylistEmail);
        api.addMember(ownerToken, stylistEmail, "STYLIST");

        String body = "{\"firstName\":\"Walk\",\"lastName\":\"In\"}";
        // Receptionist writes customers - front-desk work per the matrix.
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        // Stylist cannot.
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + stylistToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        // Both may read.
        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", "Bearer " + stylistToken))
                .andExpect(status().isOk());
    }

    @Test
    void listQueryCountDoesNotScaleWithRows() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("cust.qc"));
        api.createBusiness(token, "QC Salon");

        createCustomer(token, "QC1");
        long countSmall = countStatementsDuringList(token);

        for (int index = 2; index <= 5; index++) {
            createCustomer(token, "QC" + index);
        }
        long countLarge = countStatementsDuringList(token);

        assertThat(countLarge)
                .as("query count must not grow with rows (small=%d, large=%d)", countSmall, countLarge)
                .isEqualTo(countSmall);
    }

    private void createCustomer(String token, String firstName) throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"" + firstName + "\",\"lastName\":\"Test\"}"));
    }

    private long countStatementsDuringList(String token) throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long before = statistics.getPrepareStatementCount();
        mockMvc.perform(get("/api/v1/customers")
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
