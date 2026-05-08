package scu.dn.used_cars_backend.dto.sales;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CreateOrderResponse {

	long id;
	String orderNumber;
	String status;
}
