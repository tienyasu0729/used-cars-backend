package scu.dn.used_cars_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import scu.dn.used_cars_backend.booking.entity.Booking;

@Getter
@Setter
@Entity
@Table(name = "VehicleReviews", uniqueConstraints = {
		@UniqueConstraint(name = "UQ_vehicle_reviewer", columnNames = { "vehicle_id", "reviewer_id" })
})
public class VehicleReview extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vehicle_id", nullable = false)
	private Vehicle vehicle;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reviewer_id", nullable = false)
	private User reviewer;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

	@Column(nullable = false)
	private int rating;

	@Column(length = 2000)
	private String comment;

	@Column(nullable = false)
	private boolean anonymous = false;

	@Column(nullable = false, length = 20)
	private String status = "pending";
}
