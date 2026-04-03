package scu.dn.used_cars_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.admin.AdminBranchReportRowDto;
import scu.dn.used_cars_backend.service.AdminReportService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

	private final AdminReportService adminReportService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<AdminBranchReportRowDto>>> list() {
		return ResponseEntity.ok(ApiResponse.success(adminReportService.branchOverviewRows()));
	}
}
