package scu.dn.used_cars_backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Request body cho endpoint POST /auth/forgot-password
@Data
public class ForgotPasswordRequest {

	@NotBlank(message = "Email không được để trống.")
	@Email(message = "Email không đúng định dạng.")
	private String email;
}
