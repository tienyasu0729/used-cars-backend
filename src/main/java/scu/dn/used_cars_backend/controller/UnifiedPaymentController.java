package scu.dn.used_cars_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.payment.UnifiedPaymentDashboardDto;
import scu.dn.used_cars_backend.dto.payment.UnifiedPaymentListItemDto;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.security.JwtRoleNames;
import scu.dn.used_cars_backend.service.UnifiedPaymentQueryService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/unified-payments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER','SALESSTAFF')")
public class UnifiedPaymentController {

	private final UnifiedPaymentQueryService unifiedPaymentQueryService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<UnifiedPaymentListItemDto>>> list(
			@RequestParam(required = false) String kind,
			@RequestParam(required = false) String paymentMethods,
			@RequestParam(required = false) String statuses,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Integer branchId,
			@RequestParam(required = false) Long staffUserId,
			@RequestParam(required = false) String fromDate,
			@RequestParam(required = false) String toDate,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		List<UnifiedPaymentListItemDto> rows = unifiedPaymentQueryService.page(uid, role, kind, paymentMethods,
				statuses, keyword, branchId, staffUserId, fromDate, toDate, page, size);
		return ResponseEntity.ok(ApiResponse.success(rows,
				unifiedPaymentQueryService.pageMeta(uid, role, kind, paymentMethods, statuses, keyword, branchId,
						staffUserId, fromDate, toDate, page, size)));
	}

	@GetMapping("/dashboard")
	public ResponseEntity<ApiResponse<UnifiedPaymentDashboardDto>> dashboard(
			@RequestParam(required = false) Integer branchId,
			@RequestParam(required = false) Long staffUserId,
			@RequestParam(required = false) String fromDate,
			@RequestParam(required = false) String toDate,
			Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		return ResponseEntity.ok(ApiResponse.success(
				unifiedPaymentQueryService.dashboard(uid, role, branchId, staffUserId, fromDate, toDate)));
	}
}
