package scu.dn.used_cars_backend.dto.sales;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OrderPaymentRowDto {

	long id;
	String paymentMethod;
	String status;
	String amount;
	String transactionRef;
	String paidAt;
	String createdAt;
}
