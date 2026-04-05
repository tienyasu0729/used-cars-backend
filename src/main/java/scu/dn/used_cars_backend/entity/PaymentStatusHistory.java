package scu.dn.used_cars_backend.entity;

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
@Table(name = "PaymentStatusHistory")
public class PaymentStatusHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "target_kind", nullable = false, length = 24)
	private String targetKind;

	@Column(name = "target_id", nullable = false)
	private long targetId;

	@Column(name = "actor_user_id", nullable = false)
	private long actorUserId;

	@Column(name = "from_status", length = 40)
	private String fromStatus;

	@Column(name = "to_status", length = 40)
	private String toStatus;

	@Column(nullable = false, length = 48)
	private String action;

	@Column(length = 500)
	private String detail;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
