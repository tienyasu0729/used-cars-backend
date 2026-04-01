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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.vehicle.VehicleCreateRequest;
import scu.dn.used_cars_backend.dto.vehicle.VehicleDetailDto;
import scu.dn.used_cars_backend.dto.vehicle.VehicleListResponse;
import scu.dn.used_cars_backend.dto.vehicle.VehicleUpdateRequest;
import scu.dn.used_cars_backend.service.VehicleService;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/manager/vehicles")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER','SALESSTAFF')")
public class ManagerVehicleController {

	private final VehicleService vehicleService;

	/** Chỉ xe thuộc chi nhánh user được quản lý (Admin: mọi chi nhánh). scope=NETWORK: toàn hệ thống (read-only cho transfer). */
	@GetMapping
	public ResponseEntity<ApiResponse<VehicleListResponse>> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "100") int size,
			@RequestParam(required = false) Integer brand,
			@RequestParam(required = false) Integer subcategoryId,
			@RequestParam(required = false) BigDecimal minPrice,
			@RequestParam(required = false) BigDecimal maxPrice,
			@RequestParam(required = false) Integer yearMin,
			@RequestParam(required = false) Integer yearMax,
			@RequestParam(required = false) String transmission,
			@RequestParam(required = false) Integer branchId,
			@RequestParam(required = false) String sort,
			@RequestParam(required = false) String scope,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		Integer categoryId = brand;
		VehicleListResponse data = vehicleService.listForManager(userId, admin, categoryId, subcategoryId, minPrice,
				maxPrice, yearMin, yearMax, transmission, branchId, page, size, sort, scope);
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	/** Chi tiết để sửa — 403 nếu không quản lý chi nhánh của xe. */
	@GetMapping("/{id:\\d+}")
	public ResponseEntity<ApiResponse<VehicleDetailDto>> detail(@PathVariable long id,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		VehicleDetailDto dto = vehicleService.getManagedDetail(id, userId, admin);
		return ResponseEntity.ok(ApiResponse.success(dto));
	}

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

	/** Hiển thị lại tin đăng công khai (gỡ xóa mềm is_deleted). */
	@PostMapping("/{id}/restore-visibility")
	public ResponseEntity<ApiResponse<VehicleDetailDto>> restoreVisibility(@PathVariable long id,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		boolean admin = isAdmin(authentication);
		VehicleDetailDto dto = vehicleService.restorePublicListing(id, userId, admin);
		return ResponseEntity.ok(ApiResponse.success(dto));
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
