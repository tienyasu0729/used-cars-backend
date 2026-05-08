package scu.dn.used_cars_backend.dto.admin;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminCatalogBrandRowDto {
	String id;
	String name;
	String slug;
	long vehicleCount;
	String status;
}
