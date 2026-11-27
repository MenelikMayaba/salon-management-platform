package za.co.salon.catalogue;

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
 * Slice 5 spec part 2: commission_rules. Written before implementation (RGR).
 * Core business rule: overlapping ACTIVE effective ranges for the same employee
 * are rejected; a null effectiveTo means open-ended.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommissionRuleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createRuleRoundTripPreservesDecimalScale() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("comm.crud"));
        api.createBusiness(token, "Comm Salon");
        long employeeId = createEmployee(token);

        MvcResult created = mockMvc.perform(post("/api/v1/employees/" + employeeId + "/commission-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percentageRate\":12.5000,\"effectiveFrom\":\"2025-01-01\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(created.getResponse().getContentAsString()).contains("12.5000");
    }

    @Test
    void overlappingActiveRuleIsRejected() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("comm.overlap"));
        api.createBusiness(token, "Overlap Salon");
        long employeeId = createEmployee(token);

        // Existing open-ended rule from 2025-01-01.
        mockMvc.perform(post("/api/v1/employees/" + employeeId + "/commission-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percentageRate\":10.0000,\"effectiveFrom\":\"2025-01-01\"}"))
                .andExpect(status().isCreated());

        // A second rule starting later overlaps the open-ended one.
        MvcResult overlap = mockMvc.perform(post("/api/v1/employees/" + employeeId + "/commission-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percentageRate\":12.0000,\"effectiveFrom\":\"2025-06-01\"}"))
                .andReturn();
        assertThat(overlap.getResponse().getStatus())
                .as("overlapping active rule must be rejected, was " + overlap.getResponse().getStatus())
                .isIn(409, 422);
    }

    @Test
    void adjacentNonOverlappingRuleIsAccepted() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("comm.adjacent"));
        api.createBusiness(token, "Adjacent Salon");
        long employeeId = createEmployee(token);

        // Existing rule ending 2025-05-31.
        mockMvc.perform(post("/api/v1/employees/" + employeeId + "/commission-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percentageRate\":10.0000,\"effectiveFrom\":\"2025-01-01\""
                                + ",\"effectiveTo\":\"2025-05-31\"}"))
                .andExpect(status().isCreated());

        // New rule starting the day after - no overlap.
        mockMvc.perform(post("/api/v1/employees/" + employeeId + "/commission-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percentageRate\":12.0000,\"effectiveFrom\":\"2025-06-01\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void crossTenantEmployeeIsRejected() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String tokenA = api.registerUser(TestApiHelper.uniqueEmail("comm.xt.a"));
        api.createBusiness(tokenA, "Comm A");
        long foreignEmployeeId = createEmployee(tokenA);

        String tokenB = api.registerUser(TestApiHelper.uniqueEmail("comm.xt.b"));
        api.createBusiness(tokenB, "Comm B");

        MvcResult result = mockMvc.perform(post("/api/v1/employees/" + foreignEmployeeId + "/commission-rules")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percentageRate\":12.0000,\"effectiveFrom\":\"2025-01-01\"}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isIn(403, 404, 422);
    }

    @Test
    void roleGatesAreEnforced() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String ownerToken = api.registerUser(TestApiHelper.uniqueEmail("comm.role.owner"));
        api.createBusiness(ownerToken, "Role Salon");
        long employeeId = createEmployee(ownerToken);
        String receptionistEmail = TestApiHelper.uniqueEmail("comm.role.rec");
        String receptionistToken = api.registerUser(receptionistEmail);
        api.addMember(ownerToken, receptionistEmail, "RECEPTIONIST");

        mockMvc.perform(post("/api/v1/employees/" + employeeId + "/commission-rules")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percentageRate\":12.0000,\"effectiveFrom\":\"2025-01-01\"}"))
                .andExpect(status().isForbidden());
    }

    private long createEmployee(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Comm\",\"lastName\":\"Emp\",\"employmentStatus\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andReturn();
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
