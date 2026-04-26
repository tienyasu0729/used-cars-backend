package scu.dn.used_cars_backend.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateDocumentSessionRequest {

	@NotNull
	private Long bookingId;

	@NotBlank
	private String purpose;
}
