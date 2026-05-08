package scu.dn.used_cars_backend.interaction.entity;

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
@Table(name = "VehicleViewHistory")
public class VehicleViewHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "guest_id", nullable = false, length = 100)
	private String guestId;

	@Column(name = "user_id")
	private Long userId;

	@Column(name = "vehicle_id", nullable = false)
	private Long vehicleId;

	@Column(name = "viewed_at", nullable = false)
	private Instant viewedAt;

	@PrePersist
	void onCreate() {
		if (viewedAt == null) {
			viewedAt = Instant.now();
		}
	}
}
