package scu.dn.used_cars_backend.dto.admin;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminCatalogTypedOptionDto {
	int id;
	String name;
	String status;
	long vehicleCount;
}
