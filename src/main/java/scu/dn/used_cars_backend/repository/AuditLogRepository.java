package scu.dn.used_cars_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.AuditLog;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	@Query("""
			select a from AuditLog a
			where (:module is null or :module = '' or lower(a.module) = lower(:module))
			and (:userId is null or a.userId = :userId)
			and (:fromTs is null or a.timestamp >= :fromTs)
			and (:toTs is null or a.timestamp <= :toTs)
			and (:actionLike is null or :actionLike = '' or lower(a.action) like lower(concat('%', :actionLike, '%')))
			""")
	Page<AuditLog> search(
			@Param("module") String module,
			@Param("userId") Long userId,
			@Param("fromTs") Instant fromTs,
			@Param("toTs") Instant toTs,
			@Param("actionLike") String actionLike,
			Pageable pageable);
}
