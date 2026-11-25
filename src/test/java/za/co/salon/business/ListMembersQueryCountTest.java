package za.co.salon.business;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import za.co.salon.support.TestApiHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * N+1 regression test (blueprint rule 15). The SQL statement count for the member
 * list endpoint must be constant regardless of how many members the business has.
 * A count that grows with rows means a repository call inside a loop - the exact
 * bug this test exists to catch. Written before the fix: fails on the current
 * per-member user lookup in BusinessService.listMembers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ListMembersQueryCountTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void queryCountDoesNotScaleWithMemberCount() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);

        String ownerEmail = TestApiHelper.uniqueEmail("qc.owner");
        String ownerToken = api.registerUser(ownerEmail);
        api.createBusiness(ownerToken, "Query Count Salon");

        // Small dataset: owner + 1 member = 2 members
        String memberOne = TestApiHelper.uniqueEmail("qc.m1");
        api.registerUser(memberOne);
        api.addMember(ownerToken, memberOne, "RECEPTIONIST");

        long countSmall = countStatementsDuringMemberList(ownerToken);

        // Larger dataset: add 3 more members = 5 members
        for (int index = 2; index <= 4; index++) {
            String memberEmail = TestApiHelper.uniqueEmail("qc.m" + index);
            api.registerUser(memberEmail);
            api.addMember(ownerToken, memberEmail, "STYLIST");
        }

        long countLarge = countStatementsDuringMemberList(ownerToken);

        assertThat(countLarge)
                .as("SQL statement count must not grow with member count (small=%d, large=%d)", countSmall, countLarge)
                .isEqualTo(countSmall);
    }

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    /**
     * Performs GET /members and returns the number of SQL statements executed
     * during the request, measured via Hibernate statistics snapshots.
     * The persistence context is flushed and cleared first: without that, entities
     * loaded during setup sit in the first-level cache and findById issues no SQL,
     * hiding the N+1 this test exists to catch. Production requests always start
     * with an empty persistence context.
     */
    private long countStatementsDuringMemberList(String token) throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long before = statistics.getPrepareStatementCount();
        mockMvc.perform(get("/api/v1/businesses/current/members")
                .header("Authorization", "Bearer " + token));
        return statistics.getPrepareStatementCount() - before;
    }
}
