package scu.dn.used_cars_backend.controller;

// REST API công khai cho xe: danh sách, chi tiết. Lưu yêu thích → Tier 3.1 /users/me/saved-vehicles.

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.vehicle.VehicleDetailDto;
import scu.dn.used_cars_backend.dto.vehicle.VehicleListResponse;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.service.VehicleService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {

	private final VehicleService vehicleService;

	/** So sánh 2–3 xe công khai — query {@code ids=1,2,3} */
	@GetMapping("/compare")
	public ResponseEntity<ApiResponse<List<VehicleDetailDto>>> compare(@RequestParam String ids) {
		List<Long> idList = parseCompareIds(ids);
		List<VehicleDetailDto> data = vehicleService.comparePublic(idList);
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	private static List<Long> parseCompareIds(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thiếu tham số ids.");
		}
		String[] parts = raw.split(",");
		List<Long> out = new ArrayList<>();
		for (String p : parts) {
			String t = p != null ? p.trim() : "";
			if (t.isEmpty()) {
				continue;
			}
			try {
				out.add(Long.parseLong(t));
			}
			catch (NumberFormatException e) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "ids không hợp lệ.");
			}
		}
		if (out.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thiếu tham số ids.");
		}
		return out;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<VehicleListResponse>> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) Integer brand,
			@RequestParam(required = false) Integer subcategoryId,
			@RequestParam(required = false) BigDecimal minPrice,
			@RequestParam(required = false) BigDecimal maxPrice,
			@RequestParam(required = false) Integer yearMin,
			@RequestParam(required = false) Integer yearMax,
			@RequestParam(required = false) String transmission,
			@RequestParam(required = false) Integer branchId,
			@RequestParam(required = false) String sort,
			@RequestParam(required = false) String q) {
		Integer categoryId = brand;
		VehicleListResponse data = vehicleService.listPublic(categoryId, subcategoryId, minPrice, maxPrice, yearMin,
				yearMax, transmission, branchId, page, size, sort, q);
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	/** Chỉ khớp id số — tránh ăn path như {@code /vehicles/recently-viewed} (API Tier 3.1). */
	@GetMapping("/{id:\\d+}")
	public ResponseEntity<ApiResponse<VehicleDetailDto>> detail(@PathVariable long id,
			Authentication authentication) {
		Long userId = AuthenticationDetailsUtils.optionalUserId(authentication);
		VehicleDetailDto dto = vehicleService.getPublicDetailForUser(id, userId);
		if (dto == null) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe.");
		}
		return ResponseEntity.ok(ApiResponse.success(dto));
	}

}

