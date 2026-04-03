package scu.dn.used_cars_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.admin.ManagerReportsResponseDto;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.service.ManagerReportService;
import scu.dn.used_cars_backend.service.StaffService;

@RestController
@RequestMapping("/api/v1/manager/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER')")
public class ManagerReportController {

	private final ManagerReportService managerReportService;
	private final StaffService staffService;

	@GetMapping
	public ResponseEntity<ApiResponse<ManagerReportsResponseDto>> get(
			@RequestParam(required = false) Long branchId,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		Integer filter;
		if (admin) {
			if (branchId == null) {
				filter = null;
			} else {
				filter = Math.toIntExact(branchId);
			}
		} else {
			filter = staffService.getManagerBranchId(userId);
		}
		return ResponseEntity.ok(ApiResponse.success(managerReportService.getReports(filter)));
	}

	private static boolean isAdmin(Authentication authentication) {
		if (authentication == null) {
			return false;
		}
		for (GrantedAuthority a : authentication.getAuthorities()) {
			if ("ROLE_ADMIN".equals(a.getAuthority())) {
				return true;
			}
		}
		return false;
	}
}
