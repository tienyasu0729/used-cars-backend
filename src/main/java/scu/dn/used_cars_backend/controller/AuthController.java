package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.auth.ChangePasswordRequest;
import scu.dn.used_cars_backend.dto.auth.CompleteRequiredPasswordRequest;
import scu.dn.used_cars_backend.dto.auth.ForgotPasswordRequest;
import scu.dn.used_cars_backend.dto.auth.GoogleLoginRequest;
import scu.dn.used_cars_backend.dto.auth.LoginRequest;
import scu.dn.used_cars_backend.dto.auth.LoginResponse;
import scu.dn.used_cars_backend.dto.auth.RegisterRequest;
import scu.dn.used_cars_backend.dto.auth.RegisterResponse;
import scu.dn.used_cars_backend.dto.auth.ResetPasswordRequest;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.service.AuthService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

	// Đăng nhập / đăng ký bằng Google — nhận Google ID Token từ frontend, verify và trả JWT
	@PostMapping("/google")
	public ResponseEntity<ApiResponse<LoginResponse>> googleLogin(
			@Valid @RequestBody GoogleLoginRequest request) {
		return ResponseEntity.ok(ApiResponse.success(authService.googleLogin(request.getIdToken())));
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

	// Trả danh sách permission của user hiện tại (đọc từ SecurityContext, không gọi DB).
	// Frontend dùng endpoint này sau login để biết user có quyền gì.
	@GetMapping("/me/permissions")
	public ResponseEntity<ApiResponse<List<String>>> myPermissions(Authentication authentication) {
		AuthenticationDetailsUtils.requireUserId(authentication);
		List<String> permissions = new ArrayList<>();
		for (GrantedAuthority ga : authentication.getAuthorities()) {
			String auth = ga.getAuthority();
			// Chỉ lấy các authority dạng PERMISSION_MODULE_ACTION
			if (auth.startsWith("PERMISSION_")) {
				// PERMISSION_VEHICLES_CREATE → Vehicles.create
				String withoutPrefix = auth.substring("PERMISSION_".length());
				int underscoreIdx = withoutPrefix.indexOf('_');
				if (underscoreIdx > 0) {
					String module = withoutPrefix.substring(0, underscoreIdx);
					String action = withoutPrefix.substring(underscoreIdx + 1).toLowerCase();
					// Module: chữ cái đầu viết hoa, còn lại viết thường (VD: VEHICLES → Vehicles)
					String formattedModule = module.charAt(0) + module.substring(1).toLowerCase();
					permissions.add(formattedModule + "." + action);
				}
			}
		}
		return ResponseEntity.ok(ApiResponse.success(permissions));
	}

	// Endpoint quên mật khẩu — luôn trả 200 bất kể email có tồn tại hay không
	@PostMapping("/forgot-password")
	public ResponseEntity<ApiResponse<Map<String, Object>>> forgotPassword(
			@Valid @RequestBody ForgotPasswordRequest body) {
		authService.requestPasswordReset(body.getEmail());
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("message", "Nếu email tồn tại, chúng tôi đã gửi hướng dẫn đặt lại mật khẩu.");
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	// Endpoint đặt lại mật khẩu bằng token từ email
	@PostMapping("/reset-password")
	public ResponseEntity<ApiResponse<Map<String, Object>>> resetPassword(
			@Valid @RequestBody ResetPasswordRequest body) {
		authService.resetPassword(body.getToken(), body.getNewPassword());
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("message", "Mật khẩu đã được đặt lại thành công.");
		return ResponseEntity.ok(ApiResponse.success(data));
	}
}
