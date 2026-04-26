package scu.dn.used_cars_backend.dto.payment;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UnifiedPaymentListItemDto {

	String unifiedId;
	String kind;
	String txnRefDisplay;
	Long customerId;
	String customerName;
	String customerPhone;
	Long vehicleId;
	String vehicleTitle;
	String listingId;
	String amount;
	String paymentMethod;
	String paymentMethodLabel;
	String businessStatus;
	String gatewayStatusLabel;
	String gatewayStatusCode;
	String createdAt;
	String updatedAt;
	Integer branchId;
	String branchName;
	Long staffUserId;
	String staffName;
	Long orderId;
	Long depositId;
	Long orderPaymentId;
}
