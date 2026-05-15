package scu.dn.used_cars_backend.dto.sales;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class DepositOtpRequest {

	@NotBlank
	@Pattern(regexp = "^0\\d{9}$")
	private String phone;
}
