package scu.dn.used_cars_backend.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminCatalogModelRowDto {
	String id;
	String name;
	@JsonProperty("brandId")
	String categoryId;
	long vehicleCount;
	String status;
}
