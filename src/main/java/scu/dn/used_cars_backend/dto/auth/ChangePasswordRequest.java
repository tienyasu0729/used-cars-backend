package scu.dn.used_cars_backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Đổi mật khẩu: mật khẩu mới kiểm tra độ dài tối thiểu trong service (ErrorCode PASSWORD_TOO_SHORT).
@Data
public class ChangePasswordRequest {

	@NotBlank(message = "Mật khẩu hiện tại không được để trống.")
	private String currentPassword;

	@NotBlank(message = "Mật khẩu mới không được để trống.")
	@Size(max = 100, message = "Mật khẩu mới tối đa 100 ký tự.")
	private String newPassword;
}
