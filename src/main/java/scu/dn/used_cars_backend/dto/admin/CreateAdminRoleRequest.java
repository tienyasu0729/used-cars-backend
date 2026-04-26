package scu.dn.used_cars_backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateAdminRoleRequest {

	@NotBlank(message = "Tên vai trò không được để trống.")
	@Size(max = 50, message = "Tên vai trò tối đa 50 ký tự.")
	private String name;

	@NotEmpty(message = "Phải chọn ít nhất một quyền.")
	private List<Integer> permissionIds;
}
