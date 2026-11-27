package za.co.salon.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared helpers for integration tests: drives the real HTTP endpoints through
 * MockMvc so tests exercise the full security filter chain rather than mocking it.
 * Plain class instantiated per test - no Spring state, no shared state.
 */
public class TestApiHelper {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TestApiHelper(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /**
     * Registers a user and returns their access token.
     */
    public String registerUser(String email) throws Exception {
        String body = "{\"firstName\":\"Test\",\"lastName\":\"User\",\"email\":\"" + email
                + "\",\"password\":\"password123\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        if (result.getResponse().getStatus() != 200 && result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("Registration failed for " + email + ": "
                    + result.getResponse().getStatus() + " " + result.getResponse().getContentAsString());
        }
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    public String login(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    /**
     * Creates a business for the token's user and returns the business id.
     */
    public long createBusiness(String accessToken, String name) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"addressLine1\":\"1 Main\",\"city\":\"JHB\",\"province\":\"GP\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/businesses")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    /**
     * Adds an existing user as a member of the caller's current business.
     */
    public void addMember(String ownerToken, String email, String role) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/businesses/current/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("addMember failed: " + result.getResponse().getStatus()
                    + " " + result.getResponse().getContentAsString());
        }
    }

    /**
     * Resolves the user id for a token by reading the member list of a
     * throwaway business created for that user. Uses only public API.
     */
    public long userIdOf(String accessToken) throws Exception {
        createBusiness(accessToken, "IdProbe " + System.nanoTime());
        MvcResult result = mockMvc.perform(get("/api/v1/businesses/current/members")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode members = json.isArray() ? json : json.get("content");
        return members.get(0).get("userId").asLong();
    }

    /**
     * A unique email per call, so tests never depend on or pollute each other's data.
     */
    public static String uniqueEmail(String prefix) {
        return prefix + "." + System.nanoTime() + "@test.com";
    }
}
