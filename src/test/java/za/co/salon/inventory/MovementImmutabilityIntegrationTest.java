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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Phase 6 test #5: inventory movements are immutable audit rows. The guarantee
 * is structural - no update/delete endpoint exists anywhere - so this test pins
 * the contract against future regression: if anyone ever adds a mutation
 * endpoint, this fails and forces the conversation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MovementImmutabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void movementMutationEndpointsDoNotExist() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("inv.immutable"));
        api.createBusiness(token, "Immutable Salon");

        MvcResult putResult = mockMvc.perform(put("/api/v1/inventory/movements/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityChange\":99}"))
                .andReturn();
        assertThat(putResult.getResponse().getStatus())
                .as("PUT on movements must not exist").isIn(404, 405);

        MvcResult deleteResult = mockMvc.perform(delete("/api/v1/inventory/movements/1")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(deleteResult.getResponse().getStatus())
                .as("DELETE on movements must not exist").isIn(404, 405);

        MvcResult putAdjustment = mockMvc.perform(put("/api/v1/inventory/adjustments/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        assertThat(putAdjustment.getResponse().getStatus())
                .as("adjustments are insert-only events, never editable").isIn(404, 405);
    }
}
