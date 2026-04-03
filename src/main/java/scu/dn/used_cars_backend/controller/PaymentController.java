package scu.dn.used_cars_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.payment.OrderPaymentStaffRowDto;
import scu.dn.used_cars_backend.dto.payment.PaymentCreateRequest;
import scu.dn.used_cars_backend.dto.payment.PaymentUrlResponse;
import scu.dn.used_cars_backend.dto.payment.VnpayOrderPaymentActionRequest;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.service.payment.PaymentApplicationService;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentApplicationService paymentApplicationService;

	@PostMapping("/vnpay/create")
	public ResponseEntity<ApiResponse<PaymentUrlResponse>> createVnpay(@Valid @RequestBody PaymentCreateRequest body,
			Authentication authentication, HttpServletRequest request) {
		long uid = AuthenticationDetailsUtils.requireUserId(authentication);
		String ip = clientIp(request);
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
				clientIp(request));
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
				clientIp(request), createBy, body.getOrderInfo());
		return ResponseEntity.ok(ApiResponse.success(n));
	}

	@GetMapping("/vnpay/return")
	public void vnpayReturn(HttpServletRequest request, HttpServletResponse response) throws IOException {
		paymentApplicationService.handleVnpayReturn(request, response);
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

	private static String clientIp(HttpServletRequest request) {
		String xff = request.getHeader("X-Forwarded-For");
		if (xff != null && !xff.isBlank()) {
			int c = xff.indexOf(',');
			return c > 0 ? xff.substring(0, c).trim() : xff.trim();
		}
		String ip = request.getRemoteAddr();
		return ip != null ? ip : "127.0.0.1";
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
