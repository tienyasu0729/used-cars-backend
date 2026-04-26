package scu.dn.used_cars_backend.dto.admin;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CatalogSalesBrandRowDto {
	Integer categoryId;
	String brandName;
	long soldCount;
}
