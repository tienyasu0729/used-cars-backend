package scu.dn.used_cars_backend.controller;

// API quản lý lịch sử bảo dưỡng xe — đường dẫn /api/v1/manager/vehicles/{vehicleId}/maintenance.

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.vehicle.CreateMaintenanceRequest;
import scu.dn.used_cars_backend.dto.vehicle.MaintenanceHistoryResponse;
import scu.dn.used_cars_backend.service.MaintenanceService;

@RestController
@RequestMapping("/api/v1/manager/vehicles/{vehicleId}/maintenance")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER')")
public class MaintenanceController {

	private final MaintenanceService maintenanceService;

	/** Lấy danh sách bảo dưỡng phân trang — chỉ xe thuộc chi nhánh của actor. */
	@GetMapping
	public ResponseEntity<ApiResponse<Page<MaintenanceHistoryResponse>>> list(
			@PathVariable long vehicleId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		Page<MaintenanceHistoryResponse> data = maintenanceService
				.getMaintenanceHistory(vehicleId, userId, admin, page, size);
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	/** Tạo bản ghi bảo dưỡng mới cho xe — kiểm quyền chi nhánh. */
	@PostMapping
	public ResponseEntity<ApiResponse<MaintenanceHistoryResponse>> create(@PathVariable long vehicleId,
			@Valid @RequestBody CreateMaintenanceRequest request, Authentication authentication) {
		long userId = requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		MaintenanceHistoryResponse resp = maintenanceService
				.createMaintenanceRecord(vehicleId, request, userId, admin);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(resp));
	}

	private static long requireUserId(Authentication authentication) {
		if (authentication == null || authentication.getDetails() == null) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "Yêu cầu đăng nhập.");
		}
		if (!(authentication.getDetails() instanceof Long userId)) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "Yêu cầu đăng nhập.");
		}
		return userId;
	}

	private static boolean isAdmin(Authentication authentication) {
		for (GrantedAuthority a : authentication.getAuthorities()) {
			if ("ROLE_ADMIN".equals(a.getAuthority())) {
				return true;
			}
		}
		return false;
	}
}
