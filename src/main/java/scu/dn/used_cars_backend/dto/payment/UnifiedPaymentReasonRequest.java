package scu.dn.used_cars_backend.dto.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UnifiedPaymentReasonRequest {

	@NotBlank
	private String reason;

	private String evidenceUrl;
}
