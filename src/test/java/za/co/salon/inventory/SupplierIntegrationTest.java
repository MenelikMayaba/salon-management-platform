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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suppliers: CRUD round-trip, OWNER/MANAGER-only write gates, tenant isolation.
 * Written before implementation (RGR).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SupplierIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createListUpdateRoundTrip() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("supplier.crud"));
        api.createBusiness(token, "Supplier Salon");

        MvcResult created = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"BeautyWholesale\",\"contactPerson\":\"Sipho\""
                                + ",\"email\":\"orders@beautywholesale.co.za\",\"phoneNumber\":\"0115551234\""
                                + ",\"address\":\"12 Industrial Rd\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        assertThat(body).contains("\"name\":\"BeautyWholesale\"");
        assertThat(body).contains("orders@beautywholesale.co.za");
        long id = extractId(body);

        MvcResult updated = mockMvc.perform(put("/api/v1/suppliers/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"BeautyWholesale PTY\",\"contactPerson\":\"Sipho\""
                                + ",\"email\":\"orders@bw.co.za\",\"phoneNumber\":\"0115551234\""
                                + ",\"address\":\"14 Industrial Rd\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(updated.getResponse().getContentAsString()).contains("BeautyWholesale PTY");

        MvcResult list = mockMvc.perform(get("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String listBody = list.getResponse().getContentAsString();
        assertThat(listBody).contains("BeautyWholesale PTY");
        assertThat(listBody).doesNotContain("\"name\":\"BeautyWholesale\"");
    }

    @Test
    void receptionistCannotWriteSuppliers() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String ownerToken = api.registerUser(TestApiHelper.uniqueEmail("supplier.gate.owner"));
        api.createBusiness(ownerToken, "Gate Salon");
        String receptionistEmail = TestApiHelper.uniqueEmail("supplier.gate.recep");
        String receptionistToken = api.registerUser(receptionistEmail);
        api.addMember(ownerToken, receptionistEmail, "RECEPTIONIST");

        MvcResult result = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sneaky Supplier\"}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isIn(403, 401);
    }

    @Test
    void crossTenantSupplierInvisibleAndImmutable() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String tokenA = api.registerUser(TestApiHelper.uniqueEmail("supplier.iso.a"));
        api.createBusiness(tokenA, "Salon A");
        String tokenB = api.registerUser(TestApiHelper.uniqueEmail("supplier.iso.b"));
        api.createBusiness(tokenB, "Salon B");

        MvcResult created = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A-Supplier\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long id = extractId(created.getResponse().getContentAsString());

        MvcResult bList = mockMvc.perform(get("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(bList.getResponse().getContentAsString()).doesNotContain("A-Supplier");

        MvcResult bUpdate = mockMvc.perform(put("/api/v1/suppliers/" + id)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked\"}"))
                .andReturn();
        assertThat(bUpdate.getResponse().getStatus()).isIn(403, 404);
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
