package scu.dn.used_cars_backend.dto.sales;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CustomerOptionDto {

	String id;
	String name;
	String phone;
	String email;
}
