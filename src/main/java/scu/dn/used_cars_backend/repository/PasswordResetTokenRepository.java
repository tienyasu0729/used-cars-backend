package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import scu.dn.used_cars_backend.entity.PasswordResetToken;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

	Optional<PasswordResetToken> findByToken(String token);

	void deleteByUserId(long userId);

	void deleteByExpiresAtBefore(Instant now);
}
