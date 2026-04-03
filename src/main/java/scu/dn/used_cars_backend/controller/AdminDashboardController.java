package scu.dn.used_cars_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.admin.AdminDashboardCatalogSalesDto;
import scu.dn.used_cars_backend.dto.admin.AdminDashboardStatsDto;
import scu.dn.used_cars_backend.service.AdminDashboardService;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

	private final AdminDashboardService adminDashboardService;

	@GetMapping("/stats")
	public ResponseEntity<ApiResponse<AdminDashboardStatsDto>> stats() {
		return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getStats()));
	}

	@GetMapping("/catalog-sales")
	public ResponseEntity<ApiResponse<AdminDashboardCatalogSalesDto>> catalogSales(
			@RequestParam(name = "includeBrands", defaultValue = "true") boolean includeBrands) {
		return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getCatalogSales(includeBrands)));
	}
}
