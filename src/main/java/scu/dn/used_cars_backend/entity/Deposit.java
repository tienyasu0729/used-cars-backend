package scu.dn.used_cars_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "Deposits")
public class Deposit {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "customer_id", nullable = false)
	private Long customerId;

	@Column(name = "vehicle_id", nullable = false)
	private Long vehicleId;

	@Column(nullable = false, precision = 18, scale = 0)
	private BigDecimal amount;

	@Column(name = "payment_method", nullable = false, length = 30)
	private String paymentMethod;

	@Column(name = "deposit_date", nullable = false)
	private LocalDate depositDate;

	@Column(name = "expiry_date", nullable = false)
	private LocalDate expiryDate;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(name = "order_id")
	private Long orderId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
}
