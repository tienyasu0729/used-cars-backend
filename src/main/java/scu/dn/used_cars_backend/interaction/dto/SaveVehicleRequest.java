package scu.dn.used_cars_backend.interaction.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveVehicleRequest {

	@NotNull(message = "Thiếu mã xe (vehicleId hoặc vehicle_id).")
	@Positive(message = "vehicleId phải là số dương.")
	@JsonProperty("vehicleId")
	@JsonAlias({ "vehicle_id" })
	private Long vehicleId;
}
