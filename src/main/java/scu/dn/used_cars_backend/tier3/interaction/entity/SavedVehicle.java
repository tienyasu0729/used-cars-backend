package scu.dn.used_cars_backend.tier3.interaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;

import java.time.Instant;

// Entity map bảng SavedVehicles — khách đánh dấu xe yêu thích (khóa user_id + vehicle_id).
@Getter
@Setter
@Entity
@Table(name = "SavedVehicles")
public class SavedVehicle {

	@EmbeddedId
	private SavedVehicleId id = new SavedVehicleId();

	@MapsId("userId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@MapsId("vehicleId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vehicle_id", nullable = false)
	private Vehicle vehicle;

	@Column(name = "saved_at", nullable = false, updatable = false)
	private Instant savedAt;

	@PrePersist
	void onCreate() {
		if (savedAt == null) {
			savedAt = Instant.now();
		}
	}
}
