package za.co.salon.finance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.salon.finance.accounting.SequenceNumberService;
import za.co.salon.support.TestApiHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 test #7: concurrent number allocation. Deliberately NOT @Transactional -
 * the whole point is two threads committing independently through real connections,
 * serialized by the FOR UPDATE row lock on the counter row. The rest of the suite
 * rolls back; this class commits and leaves unique-per-run rows in sms_test, which
 * is acceptable because every query in the system is business-scoped.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SequenceNumberConcurrencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SequenceNumberService sequenceNumberService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void parallelAllocationSameBusinessYieldsDistinctSequentialNumbers() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("seq.same"));
        long businessId = api.createBusiness(token, "Seq Salon");

        // Both threads must be inside the lock at overlapping times: the latch
        // releases them together so neither can finish before the other starts.
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return transactionTemplate.execute(status ->
                            sequenceNumberService.next(businessId, "journal_entry"));
                }));
            }
            start.countDown();

            List<Long> values = new ArrayList<>();
            for (Future<Long> future : futures) {
                values.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(values).as("distinct numbers, no duplicate under race")
                    .doesNotHaveDuplicates();
            assertThat(values).as("gapless: first two allocations are 1 and 2 in some order")
                    .containsExactlyInAnyOrder(1L, 2L);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void differentBusinessesEachStartAtOne() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String tokenA = api.registerUser(TestApiHelper.uniqueEmail("seq.a"));
        long businessA = api.createBusiness(tokenA, "Seq A");
        String tokenB = api.registerUser(TestApiHelper.uniqueEmail("seq.b"));
        long businessB = api.createBusiness(tokenB, "Seq B");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Long> futureA = pool.submit(() -> {
                start.await();
                return transactionTemplate.execute(status ->
                        sequenceNumberService.next(businessA, "journal_entry"));
            });
            Future<Long> futureB = pool.submit(() -> {
                start.await();
                return transactionTemplate.execute(status ->
                        sequenceNumberService.next(businessB, "journal_entry"));
            });
            start.countDown();

            // Independent counters: neither business waits on or affects the other.
            assertThat(futureA.get(30, TimeUnit.SECONDS)).isEqualTo(1L);
            assertThat(futureB.get(30, TimeUnit.SECONDS)).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
    }
}
