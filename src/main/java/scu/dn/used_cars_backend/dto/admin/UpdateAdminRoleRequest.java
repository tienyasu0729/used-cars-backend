package scu.dn.used_cars_backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateAdminRoleRequest {

	@NotBlank(message = "Tên vai trò không được để trống.")
	@Size(max = 50, message = "Tên vai trò tối đa 50 ký tự.")
	private String name;

	@NotNull(message = "Danh sách quyền không được null.")
	private List<Integer> permissionIds;
}
