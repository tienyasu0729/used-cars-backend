package scu.dn.used_cars_backend.dto.sales;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddManualPaymentRequest {

	@NotNull
	@DecimalMin("1")
	private BigDecimal amount;

	@NotBlank
	private String paymentMethod;

	private String transactionRef;
}
