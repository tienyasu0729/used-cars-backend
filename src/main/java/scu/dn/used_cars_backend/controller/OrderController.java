package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.payment.OrderPaymentStaffRowDto;
import scu.dn.used_cars_backend.dto.sales.AddManualPaymentRequest;
import scu.dn.used_cars_backend.dto.sales.CancelOrderRequest;
import scu.dn.used_cars_backend.dto.sales.CreateOrderRequest;
import scu.dn.used_cars_backend.dto.sales.CreateOrderResponse;
import scu.dn.used_cars_backend.dto.sales.OrderDetailDto;
import scu.dn.used_cars_backend.dto.sales.OrderRowDto;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.security.JwtRoleNames;
import scu.dn.used_cars_backend.service.OrderService;
import scu.dn.used_cars_backend.service.payment.PaymentApplicationService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;
	private final PaymentApplicationService paymentApplicationService;

	@PostMapping
	@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER','SALESSTAFF')")
	public ResponseEntity<ApiResponse<CreateOrderResponse>> create(@Valid @RequestBody CreateOrderRequest body,
			Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		CreateOrderResponse r = orderService.create(uid, role, body);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(r));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<OrderRowDto>>> list(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		List<OrderRowDto> rows = orderService.list(uid, role, status, search, page, size);
		return ResponseEntity.ok(ApiResponse.success(rows, orderService.listMeta(uid, role, status, search, page, size)));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<OrderDetailDto>> get(@PathVariable long id, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		return ResponseEntity.ok(ApiResponse.success(orderService.getById(uid, role, id)));
	}

	@PatchMapping("/{id}/status")
	@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER','SALESSTAFF')")
	public ResponseEntity<ApiResponse<Void>> patchStatus(@PathVariable long id, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		orderService.advanceStatus(uid, role, id);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@PatchMapping("/{id}/cancel")
	public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable long id,
			@RequestBody(required = false) CancelOrderRequest body, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		orderService.cancel(uid, role, id, body);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@PatchMapping("/{id}/confirm-sold")
	@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER','SALESSTAFF')")
	public ResponseEntity<ApiResponse<Void>> confirmSold(@PathVariable long id, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		orderService.confirmSold(uid, role, id);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@PostMapping("/{id}/payments")
	@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER','SALESSTAFF')")
	public ResponseEntity<ApiResponse<Void>> addPayment(@PathVariable long id,
			@Valid @RequestBody AddManualPaymentRequest body, Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		orderService.addManualPayment(uid, role, id, body);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
	}

	@GetMapping("/{id}/payments")
	public ResponseEntity<ApiResponse<List<OrderPaymentStaffRowDto>>> listPayments(@PathVariable long id,
			Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		List<OrderPaymentStaffRowDto> rows;
		if ("CUSTOMER".equals(role)) {
			rows = paymentApplicationService.listPaymentsForCustomer(uid, id);
		}
		else {
			rows = paymentApplicationService.listPaymentsForStaffOrder(uid, JwtRoleNames.isAdmin(auth), id);
		}
		return ResponseEntity.ok(ApiResponse.success(rows));
	}
}
