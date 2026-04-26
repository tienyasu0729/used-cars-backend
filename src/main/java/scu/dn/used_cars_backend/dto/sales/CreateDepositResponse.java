package scu.dn.used_cars_backend.dto.sales;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CreateDepositResponse {

	long id;
	long vehicleId;
	String amount;
	String status;
	String paymentUrl;
	String depositDate;
	String expiryDate;
}
