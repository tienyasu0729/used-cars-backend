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

import java.time.Instant;

// Phiếu tư vấn từ khách — map bảng Consultations (Sprint 9).
@Getter
@Setter
@Entity
@Table(name = "Consultations")
public class Consultation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private User customer;

	@Column(name = "customer_name", nullable = false, length = 100)
	private String customerName;

	@Column(name = "customer_phone", nullable = false, length = 20)
	private String customerPhone;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "vehicle_id")
	private Vehicle vehicle;

	@Column(nullable = false, length = 1000)
	private String message;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(nullable = false, length = 10)
	private String priority;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assigned_staff_id")
	private User assignedStaff;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		Instant n = Instant.now();
		if (createdAt == null) {
			createdAt = n;
		}
		if (updatedAt == null) {
			updatedAt = n;
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}
