package scu.dn.used_cars_backend.controller;

// API quản lý xe cho Admin / BranchManager — đường dẫn /api/v1/manager/vehicles (không đổi).

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.vehicle.VehicleCreateRequest;
import scu.dn.used_cars_backend.dto.vehicle.VehicleDetailDto;
import scu.dn.used_cars_backend.dto.vehicle.VehicleUpdateRequest;
import scu.dn.used_cars_backend.service.VehicleService;

@RestController
@RequestMapping("/api/v1/manager/vehicles")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER','SALESSTAFF')")
public class ManagerVehicleController {

	private final VehicleService vehicleService;

	@PostMapping
	public ResponseEntity<ApiResponse<VehicleDetailDto>> create(@Valid @RequestBody VehicleCreateRequest request,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		VehicleDetailDto dto = vehicleService.createVehicle(request, userId, admin);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(dto));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<VehicleDetailDto>> update(@PathVariable long id,
			@Valid @RequestBody VehicleUpdateRequest request, Authentication authentication) {
		long userId = requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		VehicleDetailDto dto = vehicleService.updateVehicle(id, request, userId, admin);
		return ResponseEntity.ok(ApiResponse.success(dto));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable long id, Authentication authentication) {
		long userId = requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		vehicleService.softDeleteVehicle(id, userId, admin);
		return ResponseEntity.ok(ApiResponse.<Void>success(null));
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
