package scu.dn.used_cars_backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// DTO nhận Google ID Token từ frontend khi user đăng nhập bằng Google
@Data
public class GoogleLoginRequest {

	@NotBlank(message = "Google ID Token không được để trống.")
	private String idToken;
}
