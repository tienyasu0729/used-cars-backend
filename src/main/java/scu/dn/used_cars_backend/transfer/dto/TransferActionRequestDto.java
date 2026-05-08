package scu.dn.used_cars_backend.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Approve / Reject — note bắt buộc (FE min 5 ký tự).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferActionRequestDto {

	@NotBlank(message = "Ghi chú không được để trống.")
	@Size(min = 5, max = 500, message = "Ghi chú từ 5 đến 500 ký tự.")
	private String note;

}
