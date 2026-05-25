package scu.dn.used_cars_backend.dto.installment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectInstallmentApplicationRequest {

	@NotBlank(message = "Ly do tu choi la bat buoc.")
	@Size(max = 2000)
	private String rejectionReason;
}
