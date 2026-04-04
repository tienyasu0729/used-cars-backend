package scu.dn.used_cars_backend.dto.sales;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class OrderDetailDto {

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
	String paymentMethod;
	String status;
	String notes;
	String createdAt;
	String updatedAt;
	List<OrderPaymentRowDto> payments;
}
