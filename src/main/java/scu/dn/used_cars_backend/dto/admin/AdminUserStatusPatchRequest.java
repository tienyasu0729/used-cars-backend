package scu.dn.used_cars_backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUserStatusPatchRequest {

	@NotBlank(message = "Trạng thái không được để trống.")
	@Size(max = 20, message = "Trạng thái không hợp lệ.")
	private String status;

	/** Chưa có cột DB — chỉ nhận để tương thích API, không lưu */
	@Size(max = 500, message = "Lý do tối đa 500 ký tự.")
	private String reason;
}
