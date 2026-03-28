package scu.dn.used_cars_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

// Khóa ghép (user_id, vehicle_id) cho SavedVehicle — embed vào @EmbeddedId.
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SavedVehicleId implements Serializable {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "vehicle_id", nullable = false)
	private Long vehicleId;

}
