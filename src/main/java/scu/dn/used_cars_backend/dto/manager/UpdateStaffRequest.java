package scu.dn.used_cars_backend.dto.manager;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateStaffRequest {

	@NotBlank(message = "Họ tên không được để trống.")
	@Size(max = 100, message = "Họ tên tối đa 100 ký tự.")
	private String name;

	@NotBlank(message = "Số điện thoại không được để trống.")
	@Size(max = 20, message = "Số điện thoại tối đa 20 ký tự.")
	@Pattern(regexp = "^0[0-9]{9}$", message = "Số điện thoại phải đúng 10 chữ số và bắt đầu bằng 0.")
	private String phone;
}
