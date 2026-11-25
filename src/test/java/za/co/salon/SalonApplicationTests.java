package za.co.salon;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Context smoke test. Requires the local MySQL sms_test schema (see
 * src/test/resources/application.properties). Deferred per phase-01 note:
 * not executed until integration-test infrastructure is settled.
 */
@SpringBootTest
class SalonApplicationTests {

    @Test
    void contextLoads() {
    }
}
