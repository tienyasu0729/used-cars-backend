package scu.dn.used_cars_backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Đổi mật khẩu: quy tắc độ dài mật mới khớp RegisterRequest.password (8–100 ký tự).
@Data
public class ChangePasswordRequest {

	@NotBlank(message = "Mật khẩu hiện tại không được để trống.")
	private String currentPassword;

	@NotBlank(message = "Mật khẩu mới không được để trống.")
	@Size(min = 8, max = 100, message = "Mật khẩu từ 8 đến 100 ký tự.")
	private String newPassword;
}
