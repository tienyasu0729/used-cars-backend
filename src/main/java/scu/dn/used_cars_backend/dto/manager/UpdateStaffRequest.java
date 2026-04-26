package scu.dn.used_cars_backend.dto.manager;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateStaffRequest {

	@NotBlank(message = "Họ tên không được để trống.")
	@Size(max = 100, message = "Họ tên tối đa 100 ký tự.")
	private String name;

	@Size(max = 20, message = "Số điện thoại tối đa 20 ký tự.")
	private String phone;
}
