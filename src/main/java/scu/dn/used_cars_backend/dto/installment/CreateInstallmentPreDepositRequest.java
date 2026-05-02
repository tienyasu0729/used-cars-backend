package scu.dn.used_cars_backend.dto.installment;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateInstallmentPreDepositRequest {
	@NotBlank(message = "Phuong thuc thanh toan la bat buoc")
	private String paymentMethod;
	private String note;
}
