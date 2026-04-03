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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "OrderPayments")
public class OrderPayment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private SalesOrder order;

	@Column(nullable = false, precision = 18, scale = 0)
	private BigDecimal amount;

	@Column(name = "payment_method", nullable = false, length = 30)
	private String paymentMethod;

	@Column(nullable = false, length = 20)
	private String status = "Pending";

	@Column(name = "transaction_ref", length = 100)
	private String transactionRef;

	@Column(name = "vnp_pay_create_date", length = 14)
	private String vnpPayCreateDate;

	@Column(name = "vnp_gateway_transaction_no", length = 100)
	private String vnpGatewayTransactionNo;

	@Column(name = "vnp_last_refund_request_id", length = 40)
	private String vnpLastRefundRequestId;

	@Column(name = "paid_at")
	private Instant paidAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
