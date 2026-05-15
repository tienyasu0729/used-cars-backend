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
@Table(name = "sms_messages")
public class SmsMessage {

	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_SENT = "SENT";
	public static final String STATUS_FAILED = "FAILED";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "phone", nullable = false, length = 20)
	private String phone;

	@Column(name = "content", nullable = false, columnDefinition = "NVARCHAR(MAX)")
	private String content;

	@Column(name = "status", nullable = false, length = 10)
	private String status = STATUS_PENDING;

	@Column(name = "device_key", length = 64)
	private String deviceKey;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "sent_at")
	private Instant sentAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
