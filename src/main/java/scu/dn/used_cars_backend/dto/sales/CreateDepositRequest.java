package scu.dn.used_cars_backend.dto.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateDepositRequest {

	@NotNull
	private Long vehicleId;

	@NotNull
	@DecimalMin("1")
	private BigDecimal amount;

	@NotBlank
	private String paymentMethod;

	private String note;

	/** Khách đã có tài khoản — XOR với showroomCustomer. */
	private Long customerId;

	/** Khách showroom mới (staff nhập tay) — XOR với customerId. */
	@Valid
	private ShowroomCustomerInfo showroomCustomer;

	private String depositDate;

	private String expiryDate;
}
