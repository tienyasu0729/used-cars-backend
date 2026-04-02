package scu.dn.used_cars_backend.dto.vehicle;

// DTO dùng cho POST /manager/vehicles/{vehicleId}/maintenance — tạo bản ghi bảo dưỡng.

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateMaintenanceRequest {

	@NotNull(message = "Ngày bảo dưỡng không được để trống.")
	private LocalDate maintenanceDate;

	@NotBlank(message = "Mô tả công việc bảo dưỡng không được để trống.")
	private String description;

	@NotNull(message = "Chi phí bảo dưỡng không được để trống.")
	private BigDecimal cost;

	/** Đơn vị thực hiện (tùy chọn). */
	private String performedBy;
}
