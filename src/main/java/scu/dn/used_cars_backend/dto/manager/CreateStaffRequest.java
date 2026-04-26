package scu.dn.used_cars_backend.dto.manager;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateStaffRequest {

	@NotBlank(message = "Họ tên không được để trống.")
	@Size(max = 100, message = "Họ tên tối đa 100 ký tự.")
	private String name;

	@NotBlank(message = "Email không được để trống.")
	@Email(message = "Email không hợp lệ.")
	@Size(max = 255, message = "Email tối đa 255 ký tự.")
	private String email;

	@NotBlank(message = "Số điện thoại không được để trống.")
	@Size(max = 20, message = "Số điện thoại tối đa 20 ký tự.")
	private String phone;

	@NotBlank(message = "Mật khẩu không được để trống.")
	@Size(min = 8, max = 100, message = "Mật khẩu từ 8 đến 100 ký tự.")
	private String password;

	@NotNull(message = "Chi nhánh không được để trống.")
	private Integer branchId;
}
