package scu.dn.used_cars_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.common.web.HttpServletClientIp;
import scu.dn.used_cars_backend.dto.payment.OrderPaymentStaffRowDto;
import scu.dn.used_cars_backend.dto.payment.PaymentCreateRequest;
import scu.dn.used_cars_backend.dto.payment.PaymentDepositMethodsDto;
import scu.dn.used_cars_backend.dto.payment.PaymentUrlResponse;
import scu.dn.used_cars_backend.dto.payment.VnpayClientReturnPayload;
import scu.dn.used_cars_backend.dto.payment.VnpayOrderPaymentActionRequest;
import scu.dn.used_cars_backend.dto.payment.ZaloPayReturnPayload;
import scu.dn.used_cars_backend.dto.payment.ZaloPayStatusResponse;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.security.JwtRoleNames;
import scu.dn.used_cars_backend.service.payment.PaymentApplicationService;
import scu.dn.used_cars_backend.service.payment.PaymentGatewayConfigService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentApplicationService paymentApplicationService;
	private final PaymentGatewayConfigService paymentGatewayConfigService;

	@GetMapping("/deposit-methods")
	public ResponseEntity<ApiResponse<PaymentDepositMethodsDto>> depositMethods(Authentication authentication) {
		String role = JwtRoleNames.primaryRole(authentication);
		boolean staffSide = !"CUSTOMER".equalsIgnoreCase(role);
		boolean cash = staffSide && paymentGatewayConfigService.isCashPaymentAllowed();
		boolean vn = paymentGatewayConfigService.isTruthy(PaymentGatewayConfigService.KEY_VNPAY_ENABLED);
		boolean zl = paymentGatewayConfigService.isTruthy(PaymentGatewayConfigService.KEY_ZALO_ENABLED);
		return ResponseEntity.ok(ApiResponse.success(PaymentDepositMethodsDto.builder()
				.cash(cash)
				.vnpay(vn)
				.zalopay(zl)
				.build()));
	}

	@PostMapping("/vnpay/create")
	public ResponseEntity<ApiResponse<PaymentUrlResponse>> createVnpay(@Valid @RequestBody PaymentCreateRequest body,
			Authentication authentication, HttpServletRequest request) {
		long uid = AuthenticationDetailsUtils.requireUserId(authentication);
		String ip = HttpServletClientIp.resolve(request);
		return ResponseEntity.ok(ApiResponse.success(paymentApplicationService.createVnpay(uid, body, ip)));
	}

	@PostMapping("/zalopay/create")
	public ResponseEntity<ApiResponse<PaymentUrlResponse>> createZaloPay(@Valid @RequestBody PaymentCreateRequest body,
			Authentication authentication) {
		long uid = AuthenticationDetailsUtils.requireUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(paymentApplicationService.createZaloPay(uid, body)));
	}

	@GetMapping("/orders/{orderId}/payments")
	@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER','SALESSTAFF')")
	public ResponseEntity<ApiResponse<List<OrderPaymentStaffRowDto>>> listOrderPayments(@PathVariable long orderId,
			Authentication authentication) {
		long uid = AuthenticationDetailsUtils.requireUserId(authentication);
		var rows = paymentApplicationService.listPaymentsForStaffOrder(uid, isAdmin(authentication), orderId);
		return ResponseEntity.ok(ApiResponse.success(rows));
	}

	@PostMapping("/vnpay/query")
	@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER','SALESSTAFF')")
	public ResponseEntity<ApiResponse<JsonNode>> vnpayQuery(@Valid @RequestBody VnpayOrderPaymentActionRequest body,
			Authentication authentication, HttpServletRequest request) {
		long uid = AuthenticationDetailsUtils.requireUserId(authentication);
		JsonNode n = paymentApplicationService.staffQueryVnpay(uid, isAdmin(authentication), body.getOrderPaymentId(),
				HttpServletClientIp.resolve(request));
		return ResponseEntity.ok(ApiResponse.success(n));
	}

	@PostMapping("/vnpay/refund")
	@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER')")
	public ResponseEntity<ApiResponse<JsonNode>> vnpayRefund(@Valid @RequestBody VnpayOrderPaymentActionRequest body,
			Authentication authentication, HttpServletRequest request) {
		long uid = AuthenticationDetailsUtils.requireUserId(authentication);
		String createBy = authentication != null && authentication.getName() != null ? authentication.getName() : "";
		if (createBy.isBlank()) {
			createBy = "uid:" + uid;
		}
		JsonNode n = paymentApplicationService.staffRefundVnpay(uid, isAdmin(authentication), body.getOrderPaymentId(),
				HttpServletClientIp.resolve(request), createBy, body.getOrderInfo());
		return ResponseEntity.ok(ApiResponse.success(n));
	}

	@GetMapping("/vnpay/return")
	public ResponseEntity<?> vnpayReturn(HttpServletRequest request,
			@RequestParam(value = "json", required = false) String json) {
		VnpayClientReturnPayload payload = paymentApplicationService.completeVnpayReturnAndBuildPayload(request);
		boolean wantJson = "1".equals(json) || "true".equalsIgnoreCase(json)
				|| (request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json"));
		if (wantJson) {
			return ResponseEntity.ok(ApiResponse.success(payload));
		}
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(paymentApplicationService.buildVnpayFrontendResultUri(payload))
				.build();
	}

	@GetMapping("/zalopay/status")
	public ResponseEntity<ApiResponse<ZaloPayStatusResponse>> zaloPayStatus(
			@RequestParam(required = false) Long orderId,
			@RequestParam(required = false) Long depositId,
			Authentication authentication) {
		long uid = AuthenticationDetailsUtils.requireUserId(authentication);
		ZaloPayStatusResponse r = paymentApplicationService.customerQueryZaloPayStatus(uid, orderId, depositId);
		return ResponseEntity.ok(ApiResponse.success(r));
	}

	@GetMapping("/zalopay/return")
	public ResponseEntity<ApiResponse<ZaloPayReturnPayload>> zaloPayReturn(
			@RequestParam(required = false) Long depositId,
			@RequestParam(required = false) Long orderId,
			Authentication authentication) {
		Long uid = authentication != null
				? AuthenticationDetailsUtils.optionalUserId(authentication)
				: null;
		ZaloPayReturnPayload result = paymentApplicationService
				.processZaloPayReturn(uid, depositId, orderId);
		return ResponseEntity.ok(ApiResponse.success(result));
	}

	@PostMapping("/order-payment/{id}/cancel")
	public ResponseEntity<ApiResponse<Void>> cancelPendingOrderPayment(@PathVariable long id,
			Authentication authentication) {
		long uid = AuthenticationDetailsUtils.requireUserId(authentication);
		paymentApplicationService.customerCancelPendingOrderPayment(uid, id);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@GetMapping(value = "/vnpay/ipn", produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, String> vnpayIpnGet(HttpServletRequest request) {
		return paymentApplicationService.handleVnpayIpn(request);
	}

	@PostMapping(value = "/vnpay/ipn", produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, String> vnpayIpnPost(HttpServletRequest request) {
		return paymentApplicationService.handleVnpayIpn(request);
	}

	@PostMapping(value = "/zalopay/callback", produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, Object> zaloCallback(@RequestBody String body) {
		return paymentApplicationService.handleZaloCallback(body);
	}

	private static boolean isAdmin(Authentication authentication) {
		if (authentication == null) {
			return false;
		}
		for (GrantedAuthority a : authentication.getAuthorities()) {
			if ("ROLE_ADMIN".equals(a.getAuthority())) {
				return true;
			}
		}
		return false;
	}
}
