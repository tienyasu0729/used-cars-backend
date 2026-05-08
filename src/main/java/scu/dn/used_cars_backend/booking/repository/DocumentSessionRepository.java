package scu.dn.used_cars_backend.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.booking.entity.DocumentSession;

import java.time.Instant;
import java.util.Optional;

public interface DocumentSessionRepository extends JpaRepository<DocumentSession, Long> {

	Optional<DocumentSession> findBySessionId(String sessionId);

	@Modifying
	@Query("UPDATE DocumentSession d SET d.status = 'EXPIRED' WHERE d.status <> 'COMPLETED' AND d.status <> 'EXPIRED' AND d.expiresAt < :now")
	int expireOldSessions(@Param("now") Instant now);
}
