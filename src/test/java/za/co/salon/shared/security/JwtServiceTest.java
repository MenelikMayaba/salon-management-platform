package za.co.salon.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for JwtService - no Spring context needed because the service
 * takes all configuration through its constructor. Expiry is tested by constructing
 * a service with a negative token lifetime, producing already-expired tokens.
 */
class JwtServiceTest {

    private static final String SECRET = "test-secret-key-with-enough-bytes-for-hmac-0123456789abcdef";

    private final JwtService service = new JwtService(SECRET, 15, 7);

    @Test
    void generatedTokenRoundTrips() {
        String token = service.generateAccessToken(42L, "user@test.com");

        Claims claims = service.parseAccessToken(token);

        assertThat(service.userIdFrom(claims)).isEqualTo(42L);
        assertThat(claims.get("email", String.class)).isEqualTo("user@test.com");
        assertThat(service.isValid(token)).isTrue();
    }

    @Test
    void tokenLifetimeMatchesConfiguredMinutes() {
        String token = service.generateAccessToken(1L, "user@test.com");

        Claims claims = service.parseAccessToken(token);

        long lifetimeSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(lifetimeSeconds).isEqualTo(15 * 60);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService expiredService = new JwtService(SECRET, -1, 7);
        String token = expiredService.generateAccessToken(1L, "user@test.com");

        assertThat(service.isValid(token)).isFalse();
        assertThatThrownBy(() -> service.parseAccessToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = service.generateAccessToken(1L, "user@test.com");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(service.isValid(tampered)).isFalse();
    }

    @Test
    void garbageTokenIsRejected() {
        assertThat(service.isValid("not-a-jwt")).isFalse();
        assertThat(service.isValid("")).isFalse();
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtService otherService = new JwtService("a-different-secret-key-altogether-0123456789abcdef", 15, 7);
        String foreignToken = otherService.generateAccessToken(1L, "user@test.com");

        assertThat(service.isValid(foreignToken)).isFalse();
    }

    @Test
    void refreshTokensAreUniquePerGeneration() {
        String first = service.generateRefreshToken();
        String second = service.generateRefreshToken();

        assertThat(first).isNotEqualTo(second);
        assertThat(first).hasSize(64);
    }

    @Test
    void refreshTokenHashIsDeterministicAndNeverTheRawToken() {
        String raw = service.generateRefreshToken();

        String hashOne = service.hashRefreshToken(raw);
        String hashTwo = service.hashRefreshToken(raw);

        assertThat(hashOne).isEqualTo(hashTwo);
        assertThat(hashOne).hasSize(64);
        assertThat(hashOne).isNotEqualTo(raw);
    }
}
