package scu.dn.used_cars_backend.transfer.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// PATCH complete — note optional.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteTransferRequestDto {

	@Size(max = 500)
	private String note;

}
