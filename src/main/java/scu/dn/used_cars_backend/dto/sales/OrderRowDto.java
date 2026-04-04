package scu.dn.used_cars_backend.dto.sales;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OrderRowDto {

	long id;
	String orderNumber;
	long customerId;
	String customerName;
	Long staffId;
	String staffName;
	int branchId;
	String branchName;
	long vehicleId;
	String vehicleTitle;
	String totalPrice;
	String depositAmount;
	String remainingAmount;
	String status;
	String createdAt;
}
