package scu.dn.used_cars_backend.dto.payment;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VnpayOrderPaymentActionRequest {

	@NotNull
	private Long orderPaymentId;

	private String orderInfo;
}
