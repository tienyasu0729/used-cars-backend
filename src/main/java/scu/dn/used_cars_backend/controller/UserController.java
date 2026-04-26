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
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.CustomerStatsResponse;
import scu.dn.used_cars_backend.dto.UpdateProfileRequest;
import scu.dn.used_cars_backend.dto.auth.UserProfileDto;
import scu.dn.used_cars_backend.dto.media.CloudinarySignedUploadDto;
import scu.dn.used_cars_backend.dto.user.AvatarUploadResponse;
import scu.dn.used_cars_backend.dto.user.SaveAvatarUrlRequest;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.service.CloudinaryUploadService;
import scu.dn.used_cars_backend.service.MediaUploadContext;
import scu.dn.used_cars_backend.service.UserService;

/**
 * Hồ sơ người dùng đăng nhập.
 * <p>
 * <strong>PUT /me</strong> trả {@link ApiResponse}{@code <}{@link UserProfileDto}{@code >} (toàn bộ profile sau cập nhật),
 * thay cho ví dụ {@code Map} chỉ có message trong prompt Sprint 1 — FE đọc trực tiếp {@code data}, không cần GET lại.
 * <p>
 * <strong>Avatar:</strong> dùng Cloudinary direct upload (giảm tải server, CDN sẵn có). Khác spec gốc
 * {@code POST + MultipartFile} lưu {@code uploads/avatars}; không đổi flow để không phá FE đã tích hợp.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	private final CloudinaryUploadService cloudinaryUploadService;

	/** {@code GET} — profile đầy đủ; cùng shape với {@link #updateMe}. */
	@GetMapping("/me")
	public ResponseEntity<ApiResponse<UserProfileDto>> getMe(Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(userService.getMeProfile(userId)));
	}

	@PutMapping("/me")
	public ResponseEntity<ApiResponse<UserProfileDto>> updateMe(@Valid @RequestBody UpdateProfileRequest body,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		userService.updateProfile(userId, body);
		return ResponseEntity.ok(ApiResponse.success(userService.getMeProfile(userId)));
	}

	/**
	 * Bước 1 — ký upload Cloudinary (file không đi qua backend).
	 * Spec Sprint 1 gốc: multipart tới server; triển khai thực tế: direct upload + {@link #saveAvatarUrl}.
	 */
	@GetMapping("/me/avatar/upload-signature")
	public ResponseEntity<ApiResponse<CloudinarySignedUploadDto>> avatarUploadSignature(Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(
				cloudinaryUploadService.buildSignedDirectUpload(MediaUploadContext.AVATAR, userId)));
	}

	/**
	 * Bước 2 — lưu {@code secure_url} sau khi client đã POST file lên Cloudinary.
	 * Response: {@code ApiResponse<AvatarUploadResponse>} (thay {@code Map} — cùng JSON field {@code avatarUrl}, FE không đổi).
	 */
	@PutMapping("/me/avatar")
	public ResponseEntity<ApiResponse<AvatarUploadResponse>> saveAvatarUrl(@Valid @RequestBody SaveAvatarUrlRequest body,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		String url = userService.saveAvatarFromCloudinaryUrl(userId, body.avatarUrl());
		return ResponseEntity.ok(ApiResponse.success(AvatarUploadResponse.builder().avatarUrl(url).build()));
	}

	@GetMapping("/me/stats")
	public ResponseEntity<ApiResponse<CustomerStatsResponse>> myStats(Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(userService.getCustomerStats(userId)));
	}
}
