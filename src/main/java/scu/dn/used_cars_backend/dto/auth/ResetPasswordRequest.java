package scu.dn.used_cars_backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Request body cho endpoint POST /auth/reset-password
@Data
public class ResetPasswordRequest {

	@NotBlank(message = "Token không được để trống.")
	private String token;

	@NotBlank(message = "Mật khẩu mới không được để trống.")
	@Size(min = 8, max = 100, message = "Mật khẩu từ 8 đến 100 ký tự.")
	private String newPassword;
}
