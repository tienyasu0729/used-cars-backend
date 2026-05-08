package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.pricing.ManagerPricingEstimateRequest;
import scu.dn.used_cars_backend.service.VehiclePricingService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/manager/vehicle-pricing")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER')")
public class ManagerVehiclePricingController {

	private final VehiclePricingService vehiclePricingService;

	@PostMapping("/estimate")
	public ResponseEntity<ApiResponse<Map<String, Object>>> estimate(@Valid @RequestBody ManagerPricingEstimateRequest request,
			Authentication authentication) {
		return ResponseEntity.ok(ApiResponse.success(vehiclePricingService.estimate(request, authentication)));
	}
}
