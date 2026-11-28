package za.co.salon.sales;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import za.co.salon.finance.entity.SequenceCounter;
import za.co.salon.finance.repository.JournalEntryRepository;
import za.co.salon.finance.repository.SequenceCounterRepository;
import za.co.salon.inventory.entity.ProductStockBalance;
import za.co.salon.inventory.repository.InventoryMovementRepository;
import za.co.salon.inventory.repository.ProductStockBalanceRepository;
import za.co.salon.sales.entity.Sale;
import za.co.salon.sales.repository.SaleRepository;
import za.co.salon.support.TestApiHelper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5 test #4: insufficient stock must roll the completion back COMPLETELY.
 *
 * Deliberately NOT @Transactional - same exception as SequenceNumberConcurrencyTest.
 * Rollback semantics only exist at real transaction boundaries: inside a
 * transaction-scoped test the completion joins the test transaction and nothing
 * ever truly rolls back, so the assertions would be meaningless. This class
 * commits its setup rows and leaves unique-per-run data in sms_test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SaleCompletionRollbackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private ProductStockBalanceRepository balanceRepository;

    @Autowired
    private InventoryMovementRepository movementRepository;

    @Autowired
    private JournalEntryRepository entryRepository;

    @Autowired
    private SequenceCounterRepository sequenceCounterRepository;

    @Test
    void insufficientStockRollsBackEverythingIncludingCounter() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("complete.rollback"));
        long businessId = api.createBusiness(token, "Rollback Salon");

        long serviceId = createService(token, "Cut", "100.0000");
        long productId = createProduct(token, "Shampoo", "150.0000");
        seedStock(businessId, productId, "1", "60.0000");

        // Draft wants 5 units; only 1 on hand.
        MvcResult draft = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemType\":\"SERVICE\",\"serviceId\":" + serviceId + ",\"quantity\":1}"
                                + ",{\"itemType\":\"PRODUCT\",\"productId\":" + productId + ",\"quantity\":5}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        long saleId = extractId(draft.getResponse().getContentAsString());

        MvcResult result = mockMvc.perform(post("/api/v1/sales/" + saleId + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isIn(409, 422);

        // Real rollback verified at a real transaction boundary: sale stays DRAFT,
        // no entries, no movements, stock untouched, counter NOT burned.
        Sale sale = saleRepository.findById(saleId).orElseThrow();
        assertThat(sale.getStatus()).as("sale must remain DRAFT").isEqualTo(Sale.Status.DRAFT);
        assertThat(sale.getSaleNumber()).as("no number may be assigned").isNull();
        assertThat(entryRepository.findByBusinessIdAndSourceTypeAndSourceId(businessId, "SALE", saleId))
                .as("no recognition entry may exist").isEmpty();
        assertThat(movementRepository.findAllByReferenceTypeAndReferenceId("SALE", saleId)).isEmpty();
        ProductStockBalance balance = balanceRepository.findByBusinessIdAndProductId(businessId, productId).orElseThrow();
        assertThat(balance.getQuantityOnHand().compareTo(new BigDecimal("1.0000")))
                .as("stock must be untouched").isZero();
        SequenceCounter counter = sequenceCounterRepository
                .findByBusinessIdAndSequenceName(businessId, "sale").orElseThrow();
        assertThat(counter.getNextValue()).as("aborted completion must not burn a sale number").isEqualTo(1L);
    }

    private void seedStock(long businessId, long productId, String quantity, String averageCost) {
        // Product creation now inserts the zero balance row (blueprint 3.6), so
        // seeding stock means UPDATING that row - inserting would violate the
        // one-balance-per-product unique constraint.
        ProductStockBalance balance = balanceRepository
                .findByBusinessIdAndProductId(businessId, productId)
                .orElseGet(ProductStockBalance::new);
        balance.setBusinessId(businessId);
        balance.setProductId(productId);
        balance.setQuantityOnHand(new BigDecimal(quantity));
        balance.setAverageUnitCost(new BigDecimal(averageCost));
        balanceRepository.save(balance);
    }

    private long createService(String token, String name, String price) throws Exception {
        MvcResult category = mockMvc.perform(post("/api/v1/service-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hair\"}"))
                .andReturn();
        long categoryId = extractId(category.getResponse().getContentAsString());
        MvcResult service = mockMvc.perform(post("/api/v1/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"" + name
                                + "\",\"durationMinutes\":30,\"price\":" + price + "}"))
                .andReturn();
        return extractId(service.getResponse().getContentAsString());
    }

    private long createProduct(String token, String name, String price) throws Exception {
        MvcResult category = mockMvc.perform(post("/api/v1/product-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Retail\"}"))
                .andReturn();
        long categoryId = extractId(category.getResponse().getContentAsString());
        MvcResult product = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId + ",\"name\":\"" + name
                                + "\",\"sellingPrice\":" + price + "}"))
                .andReturn();
        return extractId(product.getResponse().getContentAsString());
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
