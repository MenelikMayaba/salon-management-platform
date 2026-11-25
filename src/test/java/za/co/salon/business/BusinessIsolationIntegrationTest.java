package za.co.salon.business;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import za.co.salon.business.repository.BusinessMemberRepository;
import za.co.salon.support.TestApiHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The critical tenant-isolation suite. With enumerable BIGINT ids these checks are
 * the ONLY defense against cross-tenant access (blueprint 2.1) - a regression here
 * is a data breach, which is why every case runs through the real security chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BusinessIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BusinessMemberRepository memberRepository;

    @Test
    void userCannotAccessOtherBusinessViaHeader() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String ownerCToken = api.registerUser(TestApiHelper.uniqueEmail("iso.c"));
        long businessCId = api.createBusiness(ownerCToken, "Salon C");
        String ownerDToken = api.registerUser(TestApiHelper.uniqueEmail("iso.d"));
        api.createBusiness(ownerDToken, "Salon D");

        // D swaps the header to C's id - the whole attack in one line.
        mockMvc.perform(get("/api/v1/businesses/current")
                        .header("Authorization", "Bearer " + ownerDToken)
                        .header("X-Business-Id", String.valueOf(businessCId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void soleMembershipResolvesWithoutHeader() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("iso.sole"));
        long businessId = api.createBusiness(token, "Sole Salon");

        MvcResult result = mockMvc.perform(get("/api/v1/businesses/current")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("\"id\":" + businessId);
    }

    @Test
    void multiBusinessUserMustSpecifyHeaderAndCanSwitch() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);

        // C owns business C; D owns D and is receptionist of C.
        String ownerCToken = api.registerUser(TestApiHelper.uniqueEmail("multi.c"));
        long businessCId = api.createBusiness(ownerCToken, "Multi C");
        String ownerDEmail = TestApiHelper.uniqueEmail("multi.d");
        String ownerDToken = api.registerUser(ownerDEmail);
        long businessDId = api.createBusiness(ownerDToken, "Multi D");
        api.addMember(ownerCToken, ownerDEmail, "RECEPTIONIST");

        // Ambiguous without header -> rejected.
        mockMvc.perform(get("/api/v1/businesses/current")
                        .header("Authorization", "Bearer " + ownerDToken))
                .andExpect(status().isForbidden());

        // Header selects C -> sees Salon C.
        MvcResult asC = mockMvc.perform(get("/api/v1/businesses/current")
                        .header("Authorization", "Bearer " + ownerDToken)
                        .header("X-Business-Id", String.valueOf(businessCId)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(asC.getResponse().getContentAsString()).contains("Multi C");

        // Header selects D -> sees own Salon D.
        MvcResult asD = mockMvc.perform(get("/api/v1/businesses/current")
                        .header("Authorization", "Bearer " + ownerDToken)
                        .header("X-Business-Id", String.valueOf(businessDId)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(asD.getResponse().getContentAsString()).contains("Multi D");
    }

    @Test
    void receptionistCannotUpdateBusinessOrManageMembers() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String ownerCToken = api.registerUser(TestApiHelper.uniqueEmail("role.c"));
        long businessCId = api.createBusiness(ownerCToken, "Role Salon");
        String receptionistEmail = TestApiHelper.uniqueEmail("role.r");
        String receptionistToken = api.registerUser(receptionistEmail);
        api.createBusiness(receptionistToken, "Receptionist Own Salon");
        api.addMember(ownerCToken, receptionistEmail, "RECEPTIONIST");

        String updateBody = "{\"name\":\"Hacked\",\"addressLine1\":\"x\",\"city\":\"x\",\"province\":\"x\"}";
        mockMvc.perform(put("/api/v1/businesses/current")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .header("X-Business-Id", String.valueOf(businessCId))
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isForbidden());

        String addBody = "{\"email\":\"nobody@test.com\",\"role\":\"STYLIST\"}";
        mockMvc.perform(post("/api/v1/businesses/current/members")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .header("X-Business-Id", String.valueOf(businessCId))
                        .contentType(MediaType.APPLICATION_JSON).content(addBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanUpdateOwnBusiness() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String token = api.registerUser(TestApiHelper.uniqueEmail("owner.ok"));
        api.createBusiness(token, "Original Name");

        String updateBody = "{\"name\":\"Renamed Salon\",\"addressLine1\":\"9 New\",\"city\":\"CPT\",\"province\":\"WC\"}";
        MvcResult result = mockMvc.perform(put("/api/v1/businesses/current")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("Renamed Salon");
    }

    @Test
    void businessCreationCreatesOwnerMembershipAtomically() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String email = TestApiHelper.uniqueEmail("atomic");
        String token = api.registerUser(email);
        long businessId = api.createBusiness(token, "Atomic Salon");

        // The member list must contain exactly one row: the creator as OWNER.
        MvcResult members = mockMvc.perform(get("/api/v1/businesses/current/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String body = members.getResponse().getContentAsString();
        assertThat(body).contains(email);
        assertThat(body).contains("OWNER");
        assertThat(memberRepository.findByBusinessId(businessId)).hasSize(1);
    }

    @Test
    void duplicateMembershipIsConflict() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String ownerToken = api.registerUser(TestApiHelper.uniqueEmail("dup.owner"));
        api.createBusiness(ownerToken, "Dup Salon");
        String memberEmail = TestApiHelper.uniqueEmail("dup.member");
        api.registerUser(memberEmail);
        api.addMember(ownerToken, memberEmail, "STYLIST");

        String body = "{\"email\":\"" + memberEmail + "\",\"role\":\"RECEPTIONIST\"}";
        mockMvc.perform(post("/api/v1/businesses/current/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void unauthenticatedAndTamperedTokensGet401() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);

        mockMvc.perform(get("/api/v1/businesses/current"))
                .andExpect(status().isUnauthorized());

        String token = api.registerUser(TestApiHelper.uniqueEmail("tamper"));
        mockMvc.perform(get("/api/v1/businesses/current")
                        .header("Authorization", "Bearer " + token + "xyz"))
                .andExpect(status().isUnauthorized());
    }
}
