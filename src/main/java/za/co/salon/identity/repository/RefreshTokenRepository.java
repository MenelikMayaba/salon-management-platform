package za.co.salon.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.salon.identity.entity.RefreshToken;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
