package za.co.salon.sales;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import za.co.salon.sales.entity.Payment;
import za.co.salon.sales.repository.PaymentRepository;
import za.co.salon.support.TestApiHelper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5 tests #2 and #3: payment idempotent replay and overpayment rejection.
 * Written before implementation (RGR).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void replayedIdempotencyKeyReturnsSamePaymentWithoutDuplicating() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("pay.replay"));
        api.createBusiness(token, "Pay Salon");
        long saleId = draftSaleWithService(token, "pay.replaysvc");

        MvcResult first = mockMvc.perform(post("/api/v1/sales/" + saleId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"amount\":100.0000"
                                + ",\"idempotencyKey\":\"key-123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long firstId = extractId(first.getResponse().getContentAsString());

        // The retry - same key, same result, no second charge.
        MvcResult replay = mockMvc.perform(post("/api/v1/sales/" + saleId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"amount\":100.0000"
                                + ",\"idempotencyKey\":\"key-123\"}"))
                .andReturn();
        assertThat(replay.getResponse().getStatus()).isIn(200, 201);
        assertThat(extractId(replay.getResponse().getContentAsString())).isEqualTo(firstId);

        List<Payment> payments = paymentRepository.findAllBySaleId(saleId);
        assertThat(payments).as("replayed key must not create a second payment row").hasSize(1);
    }

    @Test
    void overpaymentBeyondOutstandingBalanceIsRejected() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("pay.over"));
        api.createBusiness(token, "Over Salon");
        long saleId = draftSaleWithService(token, "pay.oversvc");

        // Total is 100; pay 60, then 50 exceeds the outstanding 40.
        mockMvc.perform(post("/api/v1/sales/" + saleId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"amount\":60.0000"
                                + ",\"idempotencyKey\":\"over-1\"}"))
                .andExpect(status().isCreated());

        MvcResult overpayment = mockMvc.perform(post("/api/v1/sales/" + saleId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"EFT\",\"amount\":50.0000"
                                + ",\"idempotencyKey\":\"over-2\"}"))
                .andReturn();
        assertThat(overpayment.getResponse().getStatus())
                .as("overpayment must be rejected, was " + overpayment.getResponse().getStatus())
                .isIn(400, 422);

        // Exactly the outstanding balance is fine.
        mockMvc.perform(post("/api/v1/sales/" + saleId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"EFT\",\"amount\":40.0000"
                                + ",\"idempotencyKey\":\"over-3\"}"))
                .andExpect(status().isCreated());
    }

    private long draftSaleWithService(String token, String emailPrefix) throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        MvcResult category = mockMvc.perform(post("/api/v1/service-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hair\"}"))
                .andReturn();
        long categoryId = extractId(category.getResponse().getContentAsString());
        MvcResult service = mockMvc.perform(post("/api/v1/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"Cut\""
                                + ",\"durationMinutes\":30,\"price\":100.0000}"))
                .andReturn();
        long serviceId = extractId(service.getResponse().getContentAsString());
        MvcResult sale = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemType\":\"SERVICE\",\"serviceId\":" + serviceId
                                + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(sale.getResponse().getContentAsString());
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
