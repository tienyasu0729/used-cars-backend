package scu.dn.used_cars_backend.dto.manager;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateStaffStatusRequest {

	@NotBlank(message = "Trạng thái không được để trống.")
	@Pattern(regexp = "active|inactive", message = "Trạng thái chỉ được active hoặc inactive.")
	private String status;
}
