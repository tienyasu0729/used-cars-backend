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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "BookingStatusHistory")
public class BookingStatusHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

	@Column(name = "old_status", length = 20)
	private String oldStatus;

	@Column(name = "new_status", nullable = false, length = 20)
	private String newStatus;

	@Column(name = "changed_by")
	private Long changedBy;

	@Column(length = 500)
	private String note;

	@Column(name = "changed_at", nullable = false, updatable = false)
	private Instant changedAt;

	@PrePersist
	void onCreate() {
		if (changedAt == null) {
			changedAt = Instant.now();
		}
	}
}
