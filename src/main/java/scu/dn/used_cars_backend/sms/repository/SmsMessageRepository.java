package scu.dn.used_cars_backend.sms.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.sms.entity.SmsMessage;

import java.time.Instant;
import java.util.List;

public interface SmsMessageRepository extends JpaRepository<SmsMessage, Long> {

	List<SmsMessage> findByStatus(String status);

	@Query("""
			select s from SmsMessage s
			where s.status = :status
			order by s.createdAt asc
			""")
	List<SmsMessage> findByStatusOrderByCreatedAtAsc(@Param("status") String status, Pageable pageable);

	boolean existsByPhoneAndContentAndCreatedAtAfter(
			@Param("phone") String phone,
			@Param("content") String content,
			@Param("since") Instant since);
}
