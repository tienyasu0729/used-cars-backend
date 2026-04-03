package scu.dn.used_cars_backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompleteRequiredPasswordRequest {

	@NotBlank(message = "Mật khẩu mới không được để trống.")
	@Size(min = 8, max = 100, message = "Mật khẩu từ 8 đến 100 ký tự.")
	private String newPassword;
}
