package scu.dn.used_cars_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Body cập nhật hồ sơ: tên, SĐT, địa chỉ — map cột Users.address (NVARCHAR 500).
@Data
public class UpdateProfileRequest {

	@NotBlank(message = "Họ tên không được để trống.")
	@Size(max = 100, message = "Họ tên tối đa 100 ký tự.")
	private String name;

	@Size(max = 20, message = "Số điện thoại tối đa 20 ký tự.")
	private String phone;

	@Size(max = 500, message = "Địa chỉ tối đa 500 ký tự.")
	private String address;
}
