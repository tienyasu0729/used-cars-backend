package scu.dn.used_cars_backend.sms.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.sms.entity.OtpVerification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

	Optional<OtpVerification> findTopByPhoneAndReferenceTypeAndStatusOrderByCreatedAtDesc(String phone, String referenceType, String status);

	Optional<OtpVerification> findTopByPhoneAndReferenceTypeOrderByCreatedAtDesc(String phone, String referenceType);

	List<OtpVerification> findByPhoneAndStatusAndExpiresAtAfter(String phone, String status, Instant now);
}
