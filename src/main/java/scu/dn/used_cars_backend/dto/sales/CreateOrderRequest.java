package scu.dn.used_cars_backend.dto.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOrderRequest {

	/** Khách đã có tài khoản — XOR với showroomCustomer. */
	private Long customerId;

	/** Khách showroom mới (staff nhập tay) — XOR với customerId. */
	@Valid
	private ShowroomCustomerInfo showroomCustomer;

	@NotNull
	private Long vehicleId;

	@NotNull
	@DecimalMin("1")
	private BigDecimal totalPrice;

	private Long depositId;

	private String paymentMethod;

	private String notes;
}
