package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.admin.ManagerReportSalesByBrandDto;
import scu.dn.used_cars_backend.dto.admin.ManagerReportsResponseDto;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;
import scu.dn.used_cars_backend.service.support.CatalogSalesSupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ManagerReportService {

	private final VehicleRepository vehicleRepository;
	private final BranchRepository branchRepository;

	@Transactional(readOnly = true)
	public ManagerReportsResponseDto getReports(Integer branchIdFilter) {
		if (branchIdFilter != null) {
			branchRepository.findByIdAndDeletedFalse(branchIdFilter)
					.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));
		}
		List<Object[]> rows = vehicleRepository.countSoldByCategory(branchIdFilter);
		List<ManagerReportSalesByBrandDto> sales = new ArrayList<>();
		for (Object[] row : rows) {
			String name = row[1] != null ? String.valueOf(row[1]) : "";
			long cnt = row[2] instanceof Long l ? l : ((Number) row[2]).longValue();
			sales.add(ManagerReportSalesByBrandDto.builder().brand(name).count(cnt).build());
		}
		return ManagerReportsResponseDto.builder()
				.monthlyRevenue(List.of(0L, 0L, 0L, 0L, 0L, 0L))
				.salesByBrand(sales)
				.topModels(CatalogSalesSupport.toModelRows(vehicleRepository.countSoldBySubcategory(branchIdFilter)))
				.staffPerformance(Collections.emptyList())
				.build();
	}
}
