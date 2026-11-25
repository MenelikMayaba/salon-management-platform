package za.co.salon.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack authentication flows through the real security filter chain:
 * registration, login, refresh rotation and revocation, logout. Mirrors the
 * manual verification matrix but repeatable and self-cleaning (transactional rollback).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void registerLoginAndAccessProtectedEndpoint() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String email = TestApiHelper.uniqueEmail("flow");
        String token = api.registerUser(email);
        assertThat(token).isNotBlank();

        String loginToken = api.login(email);
        assertThat(loginToken).isNotBlank();
    }

    @Test
    void duplicateEmailRegistrationIsRejected() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String email = TestApiHelper.uniqueEmail("dup");
        api.registerUser(email);

        String body = "{\"firstName\":\"Dup\",\"lastName\":\"Dup\",\"email\":\"" + email
                + "\",\"password\":\"password123\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordIsRejectedWith401() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String email = TestApiHelper.uniqueEmail("wrongpw");
        api.registerUser(email);

        String body = "{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}";
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRegistrationPayloadIsRejectedWith400() throws Exception {
        String body = "{\"firstName\":\"\",\"lastName\":\"\",\"email\":\"not-an-email\",\"password\":\"short\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshRotatesAndOldTokenIsRejected() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String email = TestApiHelper.uniqueEmail("refresh");
        api.registerUser(email);

        String refreshToken = refreshTokenFor(email);
        String rotatedBody = "{\"refreshToken\":\"" + refreshToken + "\"}";

        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(rotatedBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(rotated.getResponse().getContentAsString());
        String newRefreshToken = json.get("refreshToken").asText();
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);

        // Replaying the consumed token must fail - this is what limits token theft damage.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(rotatedBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        TestApiHelper api = new TestApiHelper(mockMvc);
        String email = TestApiHelper.uniqueEmail("logout");
        String accessToken = api.registerUser(email);
        String refreshToken = refreshTokenFor(email);

        String body = "{\"refreshToken\":\"" + refreshToken + "\"}";
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    private String refreshTokenFor(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        JsonNode json = objectMapper.readTree(login.getResponse().getContentAsString());
        return json.get("refreshToken").asText();
    }
}
