package scu.dn.used_cars_backend.dto.chat;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartVehicleConsultationChatRequest {

	@NotNull
	private Long vehicleId;

	private String message;
}
