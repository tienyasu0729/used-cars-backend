package scu.dn.used_cars_backend.dto.vehicle;

// DTO dùng cho DELETE /manager/vehicles/bulk-delete — xóa mềm xe hàng loạt.

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkDeleteRequest {

	@NotEmpty(message = "Danh sách vehicleIds không được để trống.")
	private List<Long> vehicleIds;
}
