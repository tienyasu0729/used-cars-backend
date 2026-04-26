package scu.dn.used_cars_backend.dto.manager;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TransferStaffRequest {

	@NotNull(message = "Chi nhánh đích không được để trống.")
	private Integer branchId;

	@NotNull(message = "Ngày bắt đầu không được để trống.")
	private LocalDate startDate;
}
