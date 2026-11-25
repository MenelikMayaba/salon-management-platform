package za.co.salon.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

/**
 * Issues and validates access tokens (JWT, short-lived) and refresh tokens
 * (opaque random strings, long-lived, stored hashed in the database).
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenMinutes;
    private final long refreshTokenDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-minutes:15}") long accessTokenMinutes,
            @Value("${jwt.refresh-token-days:7}") long refreshTokenDays) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenMinutes = accessTokenMinutes;
        this.refreshTokenDays = refreshTokenDays;
    }

    public String generateAccessToken(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenMinutes * 60)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates the token and returns its claims. Throws JwtException for
     * expired, tampered or malformed tokens - callers treat all of these as
     * unauthenticated and must not leak which one it was.
     */
    public Claims parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseAccessToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public Long userIdFrom(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Generates a new opaque refresh token. Only the SHA-256 hash of the
     * returned value may be stored - never the raw token.
     */
    public String generateRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public long getRefreshTokenDays() {
        return refreshTokenDays;
    }
}
