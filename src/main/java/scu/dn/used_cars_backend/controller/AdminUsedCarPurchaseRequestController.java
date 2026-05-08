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
import scu.dn.used_cars_backend.usedcarpurchase.dto.UsedCarPurchaseRequestActionRequest;
import scu.dn.used_cars_backend.usedcarpurchase.dto.UsedCarPurchaseRequestListResponse;
import scu.dn.used_cars_backend.usedcarpurchase.dto.UsedCarPurchaseRequestRejectRequest;
import scu.dn.used_cars_backend.usedcarpurchase.dto.UsedCarPurchaseRequestResponse;
import scu.dn.used_cars_backend.usedcarpurchase.service.UsedCarPurchaseRequestService;

@RestController
@RequestMapping("/api/v1/admin/used-car-purchase-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUsedCarPurchaseRequestController {

	private final UsedCarPurchaseRequestService service;

	@GetMapping
	public ResponseEntity<ApiResponse<UsedCarPurchaseRequestListResponse>> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) String status) {
		return ResponseEntity.ok(ApiResponse.success(service.listForAdmin(status, page, size)));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<UsedCarPurchaseRequestResponse>> getById(@PathVariable long id) {
		return ResponseEntity.ok(ApiResponse.success(service.getForAdmin(id)));
	}

	@PostMapping("/{id}/approve")
	public ResponseEntity<ApiResponse<UsedCarPurchaseRequestResponse>> approve(
			@PathVariable long id,
			@Valid @RequestBody UsedCarPurchaseRequestActionRequest request,
			Authentication authentication) {
		return ResponseEntity.ok(ApiResponse.success(service.approve(id, request, authentication)));
	}

	@PostMapping("/{id}/reject")
	public ResponseEntity<ApiResponse<UsedCarPurchaseRequestResponse>> reject(
			@PathVariable long id,
			@Valid @RequestBody UsedCarPurchaseRequestRejectRequest request,
			Authentication authentication) {
		return ResponseEntity.ok(ApiResponse.success(service.reject(id, request, authentication)));
	}
}
