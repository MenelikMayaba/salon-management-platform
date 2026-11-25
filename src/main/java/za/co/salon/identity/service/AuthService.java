package za.co.salon.identity.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.salon.identity.dto.AuthResponse;
import za.co.salon.identity.dto.LoginRequest;
import za.co.salon.identity.dto.RegisterRequest;
import za.co.salon.identity.dto.UserResponse;
import za.co.salon.identity.entity.RefreshToken;
import za.co.salon.identity.entity.User;
import za.co.salon.identity.repository.RefreshTokenRepository;
import za.co.salon.identity.repository.UserRepository;
import za.co.salon.shared.security.JwtService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Registration, login, token refresh and logout. Refresh tokens are opaque,
 * stored as SHA-256 hashes, and rotated on every refresh - a replayed old
 * token is dead the moment it is used once.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Optional<User> found = userRepository.findByEmail(request.getEmail());
        if (found.isEmpty()) {
            throw invalidCredentials();
        }
        User user = found.get();

        if (!user.isEnabled() || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken stored = findValidToken(rawRefreshToken);

        // Rotation: the old token dies here, a fresh pair is issued.
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        Optional<User> found = userRepository.findById(stored.getUserId());
        if (found.isEmpty()) {
            throw invalidCredentials();
        }
        User user = found.get();
        if (!user.isEnabled()) {
            throw invalidCredentials();
        }

        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = jwtService.hashRefreshToken(rawRefreshToken);
        Optional<RefreshToken> stored = refreshTokenRepository.findByTokenHash(hash);
        if (stored.isPresent()) {
            stored.get().setRevoked(true);
            refreshTokenRepository.save(stored.get());
        }
        // Deliberately no error when the token is unknown - logout is idempotent.
    }

    private RefreshToken findValidToken(String rawRefreshToken) {
        String hash = jwtService.hashRefreshToken(rawRefreshToken);
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(hash);
        if (found.isEmpty()) {
            throw invalidCredentials();
        }
        RefreshToken stored = found.get();

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw invalidCredentials();
        }
        return stored;
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());

        String rawRefreshToken = jwtService.generateRefreshToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(jwtService.hashRefreshToken(rawRefreshToken));
        refreshToken.setExpiresAt(Instant.now().plus(jwtService.getRefreshTokenDays(), ChronoUnit.DAYS));
        refreshTokenRepository.save(refreshToken);

        UserResponse userResponse = new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail());
        return new AuthResponse(accessToken, rawRefreshToken, userResponse);
    }

    /**
     * One generic error for every credential failure - attackers must not be
     * able to tell whether the email exists, the password was wrong, or the
     * token was revoked.
     */
    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
}
