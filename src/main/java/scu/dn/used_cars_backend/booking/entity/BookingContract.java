package scu.dn.used_cars_backend.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "BookingContracts")
public class BookingContract {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false, unique = true)
	private Booking booking;

	@Column(name = "contract_status", nullable = false, length = 30)
	private String contractStatus = "PENDING_SIGNATURE";

	@Column(name = "terms_version", nullable = false, length = 20)
	private String termsVersion;

	@Column(name = "signature_type", length = 10)
	private String signatureType;

	@JdbcTypeCode(SqlTypes.LONGNVARCHAR)
	@Column(name = "signature_url", columnDefinition = "NVARCHAR(MAX)")
	private String signatureUrl;

	@Column(name = "id_card_url", length = 500)
	private String idCardUrl;

	@Column(name = "license_url", length = 500)
	private String licenseUrl;

	@Column(name = "content_sha256", length = 64)
	private String contentSha256;

	@Column(name = "pdf_url", length = 500)
	private String pdfUrl;

	@Column(name = "signed_at")
	private Instant signedAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		if (createdAt == null) createdAt = now;
		if (updatedAt == null) updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}
