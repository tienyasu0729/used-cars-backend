package scu.dn.used_cars_backend.dto.vehicle;

// DTO dùng cho PATCH /manager/vehicles/bulk-status — đổi trạng thái xe hàng loạt.

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkStatusRequest {

	@NotEmpty(message = "Danh sách vehicleIds không được để trống.")
	private List<Long> vehicleIds;

	@NotBlank(message = "Trạng thái mới không được để trống.")
	private String status;
}
