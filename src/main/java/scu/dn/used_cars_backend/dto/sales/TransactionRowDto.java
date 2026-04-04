package scu.dn.used_cars_backend.dto.sales;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TransactionRowDto {

	long id;
	String type;
	String amount;
	String description;
	String status;
	String paymentGateway;
	String referenceType;
	Long referenceId;
	String createdAt;
}
