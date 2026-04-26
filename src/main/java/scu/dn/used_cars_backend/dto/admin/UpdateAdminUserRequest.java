package scu.dn.used_cars_backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAdminUserRequest {

	@NotBlank(message = "Họ tên không được để trống.")
	@Size(max = 100, message = "Họ tên tối đa 100 ký tự.")
	private String name;

	@Size(max = 20, message = "Số điện thoại tối đa 20 ký tự.")
	private String phone;

	@NotBlank(message = "Vai trò không được để trống.")
	@Size(max = 50, message = "Vai trò không hợp lệ.")
	private String role;

	private Integer branchId;

	/** active, inactive, locked (API) — lưu DB map locked → suspended */
	@NotBlank(message = "Trạng thái không được để trống.")
	@Size(max = 20, message = "Trạng thái không hợp lệ.")
	private String status;
}
