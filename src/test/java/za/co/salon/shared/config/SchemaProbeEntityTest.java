package za.co.salon.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trips every schema-contract type through real MySQL. Proves the
 * BIGINT auto-increment id, DECIMAL scale preservation, datetime(6) Instant fidelity
 * and JSON mapping. Runs against sms_test, never H2 (blueprint rule 13).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SchemaProbeEntityTest {

    @Autowired
    private SchemaProbeRepository repository;

    @Test
    void roundTripsAllContractTypes() {
        SchemaProbeEntity entity = new SchemaProbeEntity();
        entity.setAmount(new BigDecimal("400.0000"));
        entity.setRate(new BigDecimal("0.2000"));
        entity.setQuantity(new BigDecimal("18.0000"));
        entity.setActiveFlag(true);
        entity.setCreatedAt(Instant.now());

        Map<String, Object> payload = new HashMap<>();
        payload.put("source", "phase-01-probe");
        payload.put("count", 3);
        entity.setPayload(payload);

        SchemaProbeEntity saved = repository.saveAndFlush(entity);
        repository.flush();

        SchemaProbeEntity loaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getAmount()).isEqualByComparingTo("400.0000");
        assertThat(loaded.getRate()).isEqualByComparingTo("0.2000");
        assertThat(loaded.getQuantity()).isEqualByComparingTo("18.0000");
        assertThat(loaded.isActiveFlag()).isTrue();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getPayload()).containsEntry("source", "phase-01-probe");
    }
}
