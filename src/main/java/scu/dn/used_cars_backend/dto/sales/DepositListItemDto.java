package scu.dn.used_cars_backend.dto.sales;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DepositListItemDto {

	String id;
	String vehicleId;
	String customerId;
	String customerName;
	String vehicleTitle;
	long amount;
	String depositDate;
	String expiryDate;
	String status;
	String orderId;
}
