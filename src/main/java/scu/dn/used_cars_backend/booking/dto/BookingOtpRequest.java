package scu.dn.used_cars_backend.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class BookingOtpRequest {

	@NotBlank
	@Pattern(regexp = "^0\\d{9}$")
	private String phone;
}
