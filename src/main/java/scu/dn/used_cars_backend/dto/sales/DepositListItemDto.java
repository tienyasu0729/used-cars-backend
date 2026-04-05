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
	String vehicleImageUrl;
	long amount;
	String depositDate;
	String expiryDate;
	String createdAt;
	String status;
	String orderId;
	/** Cột gateway_txn_ref (VNPay/ZaloPay/tiền mặt…); null nếu chưa có. */
	String gatewayTxnRef;
}
