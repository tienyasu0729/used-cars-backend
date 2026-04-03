package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.dto.admin.AdminDashboardCatalogSalesDto;
import scu.dn.used_cars_backend.dto.admin.AdminDashboardStatsDto;
import scu.dn.used_cars_backend.dto.admin.CatalogSalesBrandRowDto;
import scu.dn.used_cars_backend.dto.admin.CatalogSalesModelRowDto;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;
import scu.dn.used_cars_backend.service.support.CatalogSalesSupport;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

	private final VehicleRepository vehicleRepository;
	private final UserRepository userRepository;
	private final BranchRepository branchRepository;

	@Transactional(readOnly = true)
	public AdminDashboardStatsDto getStats() {
		LocalDate first = LocalDate.now(ZoneId.systemDefault()).withDayOfMonth(1);
		Instant fromMonth = first.atStartOfDay(ZoneId.systemDefault()).toInstant();
		long newCustomers = userRepository.countCustomersCreatedSince(fromMonth);
		return AdminDashboardStatsDto.builder()
				.totalRevenue(0L)
				.totalVehiclesSold(vehicleRepository.countByDeletedFalseAndStatus("Sold"))
				.totalInventory(vehicleRepository.countByDeletedFalse())
				.newCustomers(newCustomers)
				.activeBranches(branchRepository.countByDeletedFalse())
				.build();
	}

	@Transactional(readOnly = true)
	public AdminDashboardCatalogSalesDto getCatalogSales(boolean includeBrands) {
		List<CatalogSalesModelRowDto> models =
				CatalogSalesSupport.toModelRows(vehicleRepository.countSoldBySubcategory(null));
		List<CatalogSalesBrandRowDto> brands = includeBrands
				? CatalogSalesSupport.toBrandRows(vehicleRepository.countSoldByCategory(null))
				: Collections.emptyList();
		return AdminDashboardCatalogSalesDto.builder()
				.topModels(models)
				.topBrands(brands)
				.build();
	}
}
