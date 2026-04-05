package scu.dn.used_cars_backend.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDepositMethodsDto {

	private boolean cash;
	private boolean vnpay;
	private boolean zalopay;
}
