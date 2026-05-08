package scu.dn.used_cars_backend.dto.payment;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PaymentHistoryItemDto {

	long id;
	String action;
	String fromStatus;
	String toStatus;
	String detail;
	long actorUserId;
	String createdAt;
}
