package scu.dn.used_cars_backend.booking.entity;

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

import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.JdbcTypeCode;

@Getter
@Setter
@Entity
@Table(name = "Bookings")
public class Booking {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "customer_id", nullable = false)
	private Long customerId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id", referencedColumnName = "id", insertable = false, updatable = false)
	private User customer;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vehicle_id", nullable = false)
	private Vehicle vehicle;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@Column(name = "staff_id")
	private Long staffId;

	@Column(name = "booking_date", nullable = false)
	private LocalDate bookingDate;

	@Column(name = "time_slot", nullable = false)
	@JdbcTypeCode(SqlTypes.TIME)
	private LocalTime timeSlot;

	@Column(length = 500)
	private String note;

	@Column(nullable = false, length = 20)
	private String status = "Pending";

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

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
