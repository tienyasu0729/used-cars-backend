package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.CustomerStatsResponse;
import scu.dn.used_cars_backend.dto.UpdateProfileRequest;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.service.UserService;

import java.util.LinkedHashMap;
import java.util.Map;

// API hồ sơ user đăng nhập: cập nhật thông tin, avatar, thống kê dashboard.
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@PutMapping("/me")
	public ResponseEntity<ApiResponse<Map<String, Object>>> updateMe(@Valid @RequestBody UpdateProfileRequest body,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		userService.updateProfile(userId, body);
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("success", true);
		data.put("message", "Thông tin cá nhân đã cập nhật.");
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	@PostMapping("/me/avatar")
	public ResponseEntity<ApiResponse<Map<String, String>>> uploadAvatar(@RequestParam("avatar") MultipartFile avatar,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		if (avatar == null || avatar.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Vui lòng chọn file ảnh.");
		}
		byte[] bytes;
		try {
			bytes = avatar.getBytes();
		}
		catch (Exception e) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Không đọc được file ảnh.");
		}
		String url = userService.saveAvatar(userId, bytes, avatar.getContentType());
		Map<String, String> data = new LinkedHashMap<>();
		data.put("avatarUrl", url);
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	@GetMapping("/me/stats")
	public ResponseEntity<ApiResponse<CustomerStatsResponse>> myStats(Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(userService.getCustomerStats(userId)));
	}
}
