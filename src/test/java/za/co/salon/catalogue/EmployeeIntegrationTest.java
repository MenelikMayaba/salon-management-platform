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
 * Slice 5 spec part 1: employees. Written before implementation (RGR).
 * An employee need not have a login account; a linked user must be a member
 * of the same business.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmployeeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Test
    void employeeWithoutUserAccountCrudRoundTrip() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("emp.crud"));
        api.createBusiness(token, "Emp Salon");

        MvcResult created = mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Thandi\",\"lastName\":\"Zulu\""
                                + ",\"employmentStatus\":\"ACTIVE\",\"employmentStartDate\":\"2025-01-15\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        assertThat(body).contains("Thandi");
        assertThat(body).contains("\"employmentStatus\":\"ACTIVE\"");
        long id = extractId(body);

        MvcResult updated = mockMvc.perform(put("/api/v1/employees/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Thandi\",\"lastName\":\"Zulu\""
                                + ",\"employmentStatus\":\"TERMINATED\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(updated.getResponse().getContentAsString()).contains("\"employmentStatus\":\"TERMINATED\"");
    }

    @Test
    void linkedUserMustBeMemberOfSameBusiness() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String ownerToken = api.registerUser(TestApiHelper.uniqueEmail("emp.link.owner"));
        api.createBusiness(ownerToken, "Link Salon");

        // A user who exists but is NOT a member of this business.
        String outsiderEmail = TestApiHelper.uniqueEmail("emp.link.outsider");
        String outsiderToken = api.registerUser(outsiderEmail);
        long outsiderUserId = api.userIdOf(outsiderToken);

        String bodyTemplate = "{\"firstName\":\"E\",\"lastName\":\"L\""
                + ",\"employmentStatus\":\"ACTIVE\",\"userId\":" + outsiderUserId + "}";
        MvcResult rejected = mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(bodyTemplate))
                .andReturn();
        assertThat(rejected.getResponse().getStatus())
                .as("linking a non-member user must be 404 or 422, was " + rejected.getResponse().getStatus())
                .isIn(404, 422);

        // After the user joins the business, the link is valid.
        api.addMember(ownerToken, outsiderEmail, "STYLIST");
        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(bodyTemplate))
                .andExpect(status().isCreated());
    }

    @Test
    void tenantIsolationOnListAndUpdate() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String tokenA = api.registerUser(TestApiHelper.uniqueEmail("emp.iso.a"));
        api.createBusiness(tokenA, "Iso A");
        MvcResult created = mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A-Emp\",\"lastName\":\"X\",\"employmentStatus\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long employeeId = extractId(created.getResponse().getContentAsString());

        String tokenB = api.registerUser(TestApiHelper.uniqueEmail("emp.iso.b"));
        api.createBusiness(tokenB, "Iso B");

        MvcResult bList = mockMvc.perform(get("/api/v1/employees")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(bList.getResponse().getContentAsString()).doesNotContain("A-Emp");

        MvcResult bUpdate = mockMvc.perform(put("/api/v1/employees/" + employeeId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Hijack\",\"lastName\":\"X\",\"employmentStatus\":\"ACTIVE\"}"))
                .andReturn();
        assertThat(bUpdate.getResponse().getStatus()).isIn(403, 404);
    }

    @Test
    void roleGatesAreEnforced() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String ownerToken = api.registerUser(TestApiHelper.uniqueEmail("emp.role.owner"));
        api.createBusiness(ownerToken, "Role Salon");
        String receptionistEmail = TestApiHelper.uniqueEmail("emp.role.rec");
        String receptionistToken = api.registerUser(receptionistEmail);
        api.addMember(ownerToken, receptionistEmail, "RECEPTIONIST");

        String body = "{\"firstName\":\"E\",\"lastName\":\"R\",\"employmentStatus\":\"ACTIVE\"}";
        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/employees")
                        .header("Authorization", "Bearer " + receptionistToken))
                .andExpect(status().isOk());
    }

    @Test
    void listQueryCountDoesNotScaleWithRows() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("emp.qc"));
        api.createBusiness(token, "QC Salon");

        createEmployee(token, "QC1");
        long countSmall = countStatementsDuringList(token);

        for (int index = 2; index <= 5; index++) {
            createEmployee(token, "QC" + index);
        }
        long countLarge = countStatementsDuringList(token);

        assertThat(countLarge)
                .as("query count must not grow with rows (small=%d, large=%d)", countSmall, countLarge)
                .isEqualTo(countSmall);
    }

    private void createEmployee(String token, String firstName) throws Exception {
        mockMvc.perform(post("/api/v1/employees")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"" + firstName + "\",\"lastName\":\"T\",\"employmentStatus\":\"ACTIVE\"}"));
    }

    private long countStatementsDuringList(String token) throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long before = statistics.getPrepareStatementCount();
        mockMvc.perform(get("/api/v1/employees")
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
