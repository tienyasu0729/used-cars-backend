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
import scu.dn.used_cars_backend.dto.dashboard.ManagerDashboardStatsResponse;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.service.ManagerDashboardService;
import scu.dn.used_cars_backend.service.StaffService;

@RestController
@RequestMapping("/api/v1/manager/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER')")
public class ManagerDashboardController {

	private final ManagerDashboardService managerDashboardService;
	private final StaffService staffService;

	@GetMapping("/stats")
	public ResponseEntity<ApiResponse<ManagerDashboardStatsResponse>> stats(
			@RequestParam(required = false) Long branchId,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		int resolved = staffService.resolveBranchIdForAdminOrBranchStaff(branchId, userId, admin);
		return ResponseEntity.ok(ApiResponse.success(managerDashboardService.getStats(resolved)));
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
