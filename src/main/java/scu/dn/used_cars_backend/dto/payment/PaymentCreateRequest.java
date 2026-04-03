package scu.dn.used_cars_backend.dto.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentCreateRequest {

	@NotNull
	private Long orderId;

	@NotNull
	@Positive
	private BigDecimal amount;
}
