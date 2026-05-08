package scu.dn.used_cars_backend.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.vehicle.MaintenanceHistoryResponse;
import scu.dn.used_cars_backend.dto.vehicle.SuggestionDto;
import scu.dn.used_cars_backend.dto.vehicle.VehicleDetailDto;
import scu.dn.used_cars_backend.dto.vehicle.VehicleListingFacetsDto;
import scu.dn.used_cars_backend.dto.vehicle.VehicleListResponse;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.service.MaintenanceService;
import scu.dn.used_cars_backend.service.VehicleService;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {

	private final VehicleService vehicleService;
	private final MaintenanceService maintenanceService;

	@GetMapping("/suggestions")
	public ResponseEntity<ApiResponse<List<SuggestionDto>>> suggestions(
			@RequestParam String q,
			@RequestParam(defaultValue = "8") int limit) {
		int safeLimit = Math.min(Math.max(limit, 1), 15);
		List<SuggestionDto> data = vehicleService.getSuggestions(q, safeLimit);
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	@GetMapping("/compare")
	public ResponseEntity<ApiResponse<List<VehicleDetailDto>>> compare(@RequestParam String ids) {
		List<Long> idList = parseCompareIds(ids);
		List<VehicleDetailDto> data = vehicleService.comparePublic(idList);
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<VehicleListResponse>> list(
			@RequestParam(defaultValue = "0") int page,
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
		VehicleListResponse data = vehicleService.listPublic(
				categoryId,
				subcategoryId,
				minPrice,
				maxPrice,
				yearMin,
				yearMax,
				transmission,
				branchId,
				page,
				size,
				sort,
				q);
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	@GetMapping("/facets")
	public ResponseEntity<ApiResponse<VehicleListingFacetsDto>> facets() {
		return ResponseEntity.ok(ApiResponse.success(vehicleService.getPublicListingFacets()));
	}

	@GetMapping("/{vehicleId:\\d+}/maintenance")
	public ResponseEntity<ApiResponse<Page<MaintenanceHistoryResponse>>> publicMaintenance(
			@PathVariable long vehicleId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Page<MaintenanceHistoryResponse> data = maintenanceService.getPublicMaintenanceHistory(vehicleId, page, size);
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	@GetMapping("/{idOrListingId:\\d+}")
	public ResponseEntity<ApiResponse<VehicleDetailDto>> detail(
			@PathVariable String idOrListingId,
			Authentication authentication) {
		Long userId = AuthenticationDetailsUtils.optionalUserId(authentication);
		VehicleDetailDto dto = vehicleService.getPublicDetailForUserByToken(idOrListingId, userId);
		if (dto == null) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Khong tim thay xe.");
		}
		return ResponseEntity.ok(ApiResponse.success(dto));
	}

	private static List<Long> parseCompareIds(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thieu tham so ids.");
		}
		String[] parts = raw.split(",");
		List<Long> out = new ArrayList<>();
		for (String p : parts) {
			String trimmed = p != null ? p.trim() : "";
			if (trimmed.isEmpty()) {
				continue;
			}
			try {
				out.add(Long.parseLong(trimmed));
			} catch (NumberFormatException e) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "ids khong hop le.");
			}
		}
		if (out.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thieu tham so ids.");
		}
		return out;
	}
}
