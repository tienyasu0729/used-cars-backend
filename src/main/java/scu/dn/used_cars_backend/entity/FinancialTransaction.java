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

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "Transactions")
public class FinancialTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "payment_gateway", length = 20)
	private String paymentGateway;

	@Column(nullable = false, length = 20)
	private String type;

	@Column(nullable = false, precision = 18, scale = 0)
	private BigDecimal amount;

	@Column(length = 500)
	private String description;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(name = "reference_id")
	private Long referenceId;

	@Column(name = "reference_type", length = 30)
	private String referenceType;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
