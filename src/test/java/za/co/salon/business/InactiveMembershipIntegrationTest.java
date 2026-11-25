package za.co.salon.business;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import za.co.salon.business.entity.BusinessMember;
import za.co.salon.business.repository.BusinessMemberRepository;
import za.co.salon.support.TestApiHelper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inactive memberships must be denied (phase-02 exit criterion). No endpoint flips
 * member status yet, so the test suspends the membership row directly through the
 * repository - the state exists in the schema precisely because the system must
 * handle it regardless of how it arises.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InactiveMembershipIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BusinessMemberRepository memberRepository;

    @Test
    void suspendedMemberIsDeniedForThatBusinessOnly() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);

        String ownerCToken = api.registerUser(TestApiHelper.uniqueEmail("susp.c"));
        long businessCId = api.createBusiness(ownerCToken, "Suspend C");
        String memberEmail = TestApiHelper.uniqueEmail("susp.m");
        String memberToken = api.registerUser(memberEmail);
        long ownBusinessId = api.createBusiness(memberToken, "Member Own Salon");
        api.addMember(ownerCToken, memberEmail, "RECEPTIONIST");

        // Sanity: active membership works before the suspension.
        mockMvc.perform(get("/api/v1/businesses/current")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("X-Business-Id", String.valueOf(businessCId)))
                .andExpect(status().isOk());

        // Suspend the receptionist membership in salon C through the same persistence
        // context, so subsequent queries see the new state.
        BusinessMember membership = null;
        for (BusinessMember candidate : memberRepository.findByBusinessId(businessCId)) {
            if (candidate.getRole() == BusinessMember.Role.RECEPTIONIST) {
                membership = candidate;
                break;
            }
        }
        if (membership == null) {
            throw new IllegalStateException("Test setup broken: receptionist membership not found");
        }
        membership.setStatus(BusinessMember.Status.SUSPENDED);
        memberRepository.saveAndFlush(membership);

        // Suspended: salon C rejects the member.
        mockMvc.perform(get("/api/v1/businesses/current")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("X-Business-Id", String.valueOf(businessCId)))
                .andExpect(status().isForbidden());

        // The user's own ACTIVE membership elsewhere is unaffected.
        mockMvc.perform(get("/api/v1/businesses/current")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("X-Business-Id", String.valueOf(ownBusinessId)))
                .andExpect(status().isOk());
    }
}
