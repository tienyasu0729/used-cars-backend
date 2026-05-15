package scu.dn.used_cars_backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegistrationOtpRequest {

	@NotBlank(message = "Số điện thoại không được để trống.")
	@Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải đúng 10 chữ số và bắt đầu bằng 0.")
	private String phone;

	@NotBlank(message = "Email không được để trống.")
	@Email(message = "Email không hợp lệ.")
	private String email;
}
