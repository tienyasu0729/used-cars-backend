package scu.dn.used_cars_backend.sms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "otp_verifications")
public class OtpVerification {

	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_VERIFIED = "VERIFIED";
	public static final String STATUS_EXPIRED = "EXPIRED";
	public static final String STATUS_EXHAUSTED = "EXHAUSTED";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "phone", nullable = false, length = 20)
	private String phone;

	@Column(name = "otp_code", nullable = false, length = 6)
	private String otpCode;

	@Column(name = "reference_type", nullable = false, length = 50)
	private String referenceType;

	@Column(name = "reference_id")
	private Long referenceId;

	@Column(name = "status", nullable = false, length = 10)
	private String status = STATUS_PENDING;

	@Column(name = "attempts", nullable = false)
	private int attempts = 0;

	@Column(name = "max_attempts", nullable = false)
	private int maxAttempts = 5;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "verified_at")
	private Instant verifiedAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
