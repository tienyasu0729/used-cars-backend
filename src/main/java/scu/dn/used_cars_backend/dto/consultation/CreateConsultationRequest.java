package scu.dn.used_cars_backend.dto.consultation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateConsultationRequest {

	@NotBlank
	@Size(max = 100)
	private String customerName;

	@NotBlank
	@Pattern(regexp = "^0[0-9]{9}$", message = "Số điện thoại phải 10 số bắt đầu bằng 0")
	private String customerPhone;

	private Long vehicleId;

	@NotBlank
	@Size(max = 1000)
	private String message;

	/** low | medium | high — mặc định medium nếu null */
	private String priority;
}
