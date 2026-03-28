package scu.dn.used_cars_backend.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import scu.dn.used_cars_backend.entity.Branch;

import java.time.LocalTime;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.JdbcTypeCode;

@Getter
@Setter
@Entity
@Table(name = "BookingSlots")
public class BookingSlot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@Column(name = "slot_time", nullable = false)
	@JdbcTypeCode(SqlTypes.TIME)
	private LocalTime slotTime;

	@Column(name = "max_bookings", nullable = false)
	private Integer maxBookings = 3;

	@Column(name = "is_active", nullable = false)
	private Boolean active = true;
}
