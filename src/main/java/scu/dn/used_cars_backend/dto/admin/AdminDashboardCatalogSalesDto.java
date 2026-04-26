package scu.dn.used_cars_backend.dto.admin;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AdminDashboardCatalogSalesDto {
	List<CatalogSalesModelRowDto> topModels;
	List<CatalogSalesBrandRowDto> topBrands;
}
