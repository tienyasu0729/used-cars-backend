package scu.dn.used_cars_backend.dto.admin;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class ManagerReportsResponseDto {
	List<Long> monthlyRevenue;
	List<ManagerReportSalesByBrandDto> salesByBrand;
	List<CatalogSalesModelRowDto> topModels;
	List<Map<String, Object>> staffPerformance;
}
