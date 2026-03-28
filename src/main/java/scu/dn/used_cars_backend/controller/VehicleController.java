package scu.dn.used_cars_backend.controller;

// REST API công khai cho xe: danh sách, chi tiết, lưu/bỏ lưu (cần đăng nhập cho lưu).

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.vehicle.VehicleDetailDto;
import scu.dn.used_cars_backend.dto.vehicle.VehicleListResponse;
import scu.dn.used_cars_backend.service.VehicleService;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {

	private final VehicleService vehicleService;

	@GetMapping
	public ResponseEntity<ApiResponse<VehicleListResponse>> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) Integer brand,
			@RequestParam(required = false) BigDecimal minPrice,
			@RequestParam(required = false) BigDecimal maxPrice) {
		Integer categoryId = brand;
		VehicleListResponse data = vehicleService.listPublic(categoryId, minPrice, maxPrice, page, size);
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<VehicleDetailDto>> detail(@PathVariable long id) {
		VehicleDetailDto dto = vehicleService.getPublicDetail(id);
		if (dto == null) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe.");
		}
		return ResponseEntity.ok(ApiResponse.success(dto));
	}

	@PostMapping("/{id}/save")
	public ResponseEntity<ApiResponse<Map<String, String>>> save(@PathVariable long id, Authentication authentication) {
		long userId = requireUserId(authentication);
		vehicleService.saveForUser(id, userId);
		return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Đã lưu xe.")));
	}

	@DeleteMapping("/{id}/save")
	public ResponseEntity<ApiResponse<Map<String, String>>> unsave(@PathVariable long id, Authentication authentication) {
		long userId = requireUserId(authentication);
		vehicleService.removeSavedForUser(id, userId);
		return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Đã bỏ lưu xe.")));
	}

	/** Lấy userId từ JWT filter (đặt trong authentication.details). */
	private static long requireUserId(Authentication authentication) {
		if (authentication == null || authentication.getDetails() == null) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "Yêu cầu đăng nhập.");
		}
		if (!(authentication.getDetails() instanceof Long userId)) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "Yêu cầu đăng nhập.");
		}
		return userId;
	}

}
