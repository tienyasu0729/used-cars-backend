package scu.dn.used_cars_backend.dto.sales;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOrderRequest {

	@NotNull
	private Long customerId;

	@NotNull
	private Long vehicleId;

	@NotNull
	@DecimalMin("1")
	private BigDecimal totalPrice;

	private Long depositId;

	private String paymentMethod;

	private String notes;
}
