package scu.dn.used_cars_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Map bảng Orders — đơn bán xe (read/write theo schema init_schema.sql). */
@Getter
@Setter
@Entity
@Table(name = "Orders")
public class SalesOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_number", nullable = false, unique = true, length = 20)
	private String orderNumber;

	@Column(name = "customer_id", nullable = false)
	private Long customerId;

	@Column(name = "staff_id")
	private Long staffId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vehicle_id", nullable = false)
	private Vehicle vehicle;

	@Column(name = "total_price", nullable = false, precision = 18, scale = 0)
	private BigDecimal totalPrice;

	@Column(name = "deposit_amount", nullable = false, precision = 18, scale = 0)
	private BigDecimal depositAmount = BigDecimal.ZERO;

	@Column(name = "remaining_amount", nullable = false, precision = 18, scale = 0)
	private BigDecimal remainingAmount;

	@Column(name = "payment_method", length = 30)
	private String paymentMethod;

	@Column(nullable = false, length = 20)
	private String status = "Pending";

	@Column(length = 1000)
	private String notes;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "created_by")
	private Long createdBy;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		if (updatedAt == null) {
			updatedAt = now;
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}
