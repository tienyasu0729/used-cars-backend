package scu.dn.used_cars_backend.dto.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UnifiedPaymentNoteRequest {

	@NotBlank
	private String note;
}
