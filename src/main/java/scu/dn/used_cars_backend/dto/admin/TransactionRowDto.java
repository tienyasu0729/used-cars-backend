package scu.dn.used_cars_backend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRowDto {

	private Long id;
	private String source;
	private Long sourceId;
	private String type;
	private BigDecimal amount;
	private String status;
	private String statusLabel;
	private String paymentGateway;
	private String gatewayTxnRef;
	private Long customerId;
	private String customerName;
	private String customerPhone;
	private Long vehicleId;
	private String vehicleTitle;
	private String vehicleListingId;
	private Long branchId;
	private String branchName;
	private String orderId;
	private Long depositId;
	private String createdAt;
	private String paidAt;
}
