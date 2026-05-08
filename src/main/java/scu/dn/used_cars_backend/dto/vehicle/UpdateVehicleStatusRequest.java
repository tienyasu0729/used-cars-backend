package scu.dn.used_cars_backend.dto.vehicle;

// DTO dùng cho PATCH /manager/vehicles/{id}/status — đổi trạng thái xe đơn lẻ.

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateVehicleStatusRequest {

	@NotBlank(message = "Trạng thái mới không được để trống.")
	private String status;

	/** Ghi chú (tùy chọn). */
	private String note;
}
