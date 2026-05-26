package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.user.ProfilePhoneOtpRequest;
import scu.dn.used_cars_backend.dto.user.ProfilePhoneVerifyOtpRequest;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.service.ProfilePhoneOtpService;
import scu.dn.used_cars_backend.sms.dto.OtpResponse;
import scu.dn.used_cars_backend.sms.dto.OtpVerifyResult;

@RestController
@RequestMapping("/api/v1/users/me/phone")
@RequiredArgsConstructor
public class ProfilePhoneOtpController {

	private final ProfilePhoneOtpService profilePhoneOtpService;

	@PostMapping("/request-otp")
	public ResponseEntity<ApiResponse<OtpResponse>> requestOtp(
			@Valid @RequestBody ProfilePhoneOtpRequest body,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(profilePhoneOtpService.requestOtp(userId, body.getPhone())));
	}

	@PostMapping("/verify-otp")
	public ResponseEntity<ApiResponse<OtpVerifyResult>> verifyOtp(
			@Valid @RequestBody ProfilePhoneVerifyOtpRequest body,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(
				profilePhoneOtpService.verifyOtp(userId, body.getPhone(), body.getOtpCode())));
	}

	@PostMapping("/resend-otp")
	public ResponseEntity<ApiResponse<OtpResponse>> resendOtp(
			@Valid @RequestBody ProfilePhoneOtpRequest body,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(profilePhoneOtpService.resendOtp(userId, body.getPhone())));
	}
}
