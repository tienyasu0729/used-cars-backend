package scu.dn.used_cars_backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAdminBranchRequest {

	@NotBlank(message = "Tên chi nhánh không được để trống.")
	@Size(max = 200, message = "Tên chi nhánh tối đa 200 ký tự.")
	private String name;

	@NotBlank(message = "Địa chỉ không được để trống.")
	@Size(max = 500, message = "Địa chỉ tối đa 500 ký tự.")
	private String address;

	@Size(max = 20, message = "Số điện thoại tối đa 20 ký tự.")
	private String phone;

	private Long managerId;
}
