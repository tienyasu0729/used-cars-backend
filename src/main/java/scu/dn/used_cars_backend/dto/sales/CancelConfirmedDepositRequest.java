package scu.dn.used_cars_backend.dto.sales;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CancelConfirmedDepositRequest {

	@NotBlank(message = "Ly do huy la bat buoc.")
	private String reason;
}
