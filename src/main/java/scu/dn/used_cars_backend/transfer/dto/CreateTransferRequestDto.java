package scu.dn.used_cars_backend.transfer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Body POST tạo yêu cầu — reason optional (DB nullable).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransferRequestDto {

	@NotNull
	private Long vehicleId;

	@NotNull
	private Integer toBranchId;

	@Size(max = 500)
	private String reason;

}
