package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.auth.CompleteRequiredPasswordRequest;
import scu.dn.used_cars_backend.dto.auth.ChangePasswordRequest;
import scu.dn.used_cars_backend.dto.auth.LoginRequest;
import scu.dn.used_cars_backend.dto.auth.LoginResponse;
import scu.dn.used_cars_backend.dto.auth.RegisterRequest;
import scu.dn.used_cars_backend.dto.auth.RegisterResponse;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.service.AuthService;

import java.util.LinkedHashMap;
import java.util.Map;

// API xác thực: đăng nhập, đăng ký, đổi mật khẩu, logout (placeholder JWT stateless).
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
	}

	@PostMapping("/register")
	public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(authService.register(request)));
	}

	@PostMapping("/complete-required-password-change")
	public ResponseEntity<ApiResponse<LoginResponse>> completeRequiredPasswordChange(
			@Valid @RequestBody CompleteRequiredPasswordRequest body,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		LoginResponse out = authService.completeRequiredPasswordChange(userId, body.getNewPassword());
		return ResponseEntity.ok(ApiResponse.success(out));
	}

	@PostMapping("/change-password")
	public ResponseEntity<ApiResponse<Map<String, Object>>> changePassword(@Valid @RequestBody ChangePasswordRequest body,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		authService.changePassword(userId, body.getCurrentPassword(), body.getNewPassword());
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("success", true);
		data.put("message", "Mật khẩu đã được thay đổi.");
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Map<String, Object>>> logout() {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("success", true);
		data.put("message", "Đã đăng xuất thành công.");
		return ResponseEntity.ok(ApiResponse.success(data));
	}
}
