package scu.dn.used_cars_backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.common.web.HttpServletClientIp;
import scu.dn.used_cars_backend.dto.sales.CreateDepositResponse;
import scu.dn.used_cars_backend.dto.sales.DepositOtpRequest;
import scu.dn.used_cars_backend.dto.sales.DepositVerifyOtpRequest;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.security.JwtRoleNames;
import scu.dn.used_cars_backend.service.DepositOtpService;
import scu.dn.used_cars_backend.sms.dto.OtpResponse;
import scu.dn.used_cars_backend.sms.service.OtpService;

@RestController
@RequestMapping("/api/v1/deposits")
@RequiredArgsConstructor
public class DepositOtpController {

	private static final String REFERENCE_TYPE_DEPOSIT = "deposit";

	private final DepositOtpService depositOtpService;
	private final OtpService otpService;

	@PostMapping("/request-otp")
	@PreAuthorize("hasAnyRole('CUSTOMER','SALESSTAFF','BRANCHMANAGER','ADMIN')")
	public ResponseEntity<ApiResponse<OtpResponse>> requestOtp(
			@Valid @RequestBody DepositOtpRequest body,
			Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		OtpResponse response = depositOtpService.requestOtp(uid, body.getPhone());
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@PostMapping("/resend-otp")
	@PreAuthorize("hasAnyRole('CUSTOMER','SALESSTAFF','BRANCHMANAGER','ADMIN')")
	public ResponseEntity<ApiResponse<OtpResponse>> resendOtp(
			@Valid @RequestBody DepositOtpRequest body,
			Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		otpService.findPendingOtp(body.getPhone(), REFERENCE_TYPE_DEPOSIT)
				.ifPresent(otp -> otpService.invalidateOtp(otp.getId()));
		OtpResponse response = depositOtpService.requestOtp(uid, body.getPhone());
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@PostMapping("/verify-otp")
	@PreAuthorize("hasAnyRole('CUSTOMER','SALESSTAFF','BRANCHMANAGER','ADMIN')")
	public ResponseEntity<ApiResponse<CreateDepositResponse>> verifyOtpAndCreate(
			@Valid @RequestBody DepositVerifyOtpRequest body,
			Authentication auth,
			HttpServletRequest request) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		String role = JwtRoleNames.primaryRole(auth);
		String clientIp = HttpServletClientIp.resolve(request);
		CreateDepositResponse response = depositOtpService.verifyOtpAndCreateDeposit(
				uid, role, body, clientIp);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
	}
}
