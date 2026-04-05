package scu.dn.used_cars_backend.dto.consultation;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PatchConsultationStatusRequest {

	@NotBlank
	private String status;
}
