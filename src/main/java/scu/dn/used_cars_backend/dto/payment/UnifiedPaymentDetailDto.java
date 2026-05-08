package scu.dn.used_cars_backend.dto.payment;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UnifiedPaymentDetailDto {

	UnifiedPaymentListItemDto summary;
	String gatewayTxnRefFull;
	String lastGatewayQueryJson;
	String lastGatewayQueryAt;
	String internalNotes;
}
