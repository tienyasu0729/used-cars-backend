package scu.dn.used_cars_backend.dto.admin;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CatalogSalesModelRowDto {
	Integer subcategoryId;
	String modelName;
	String brandName;
	long soldCount;
}
