package scu.dn.used_cars_backend.tier3.interaction.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveVehicleRequest {

	@NotNull
	private Long vehicleId;
}
