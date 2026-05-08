package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.usedcarpurchase.dto.UsedCarPurchaseRequestCreateRequest;
import scu.dn.used_cars_backend.usedcarpurchase.dto.UsedCarPurchaseRequestListResponse;
import scu.dn.used_cars_backend.usedcarpurchase.dto.UsedCarPurchaseRequestResponse;
import scu.dn.used_cars_backend.usedcarpurchase.service.UsedCarPurchaseRequestService;

@RestController
@RequestMapping("/api/v1/manager/used-car-purchase-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER')")
public class ManagerUsedCarPurchaseRequestController {

	private final UsedCarPurchaseRequestService service;

	@PostMapping
	public ResponseEntity<ApiResponse<UsedCarPurchaseRequestResponse>> create(
			@Valid @RequestBody UsedCarPurchaseRequestCreateRequest request,
			Authentication authentication) {
		return ResponseEntity.ok(ApiResponse.success(service.create(request, authentication)));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<UsedCarPurchaseRequestListResponse>> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) String status,
			Authentication authentication) {
		return ResponseEntity.ok(ApiResponse.success(service.listForManager(authentication, status, page, size)));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<UsedCarPurchaseRequestResponse>> getById(
			@PathVariable long id,
			Authentication authentication) {
		return ResponseEntity.ok(ApiResponse.success(service.getForManager(id, authentication)));
	}

	@PostMapping("/{id}/mark-paid")
	public ResponseEntity<ApiResponse<UsedCarPurchaseRequestResponse>> markPaid(
			@PathVariable long id,
			Authentication authentication) {
		return ResponseEntity.ok(ApiResponse.success(service.markPaid(id, authentication)));
	}
}
