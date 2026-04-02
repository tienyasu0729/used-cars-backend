package scu.dn.used_cars_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.manager.MediaUploadStatusDto;
import scu.dn.used_cars_backend.dto.media.CloudinarySignedUploadDto;
import scu.dn.used_cars_backend.service.CloudinaryUploadService;
import scu.dn.used_cars_backend.service.MediaUploadContext;

@RestController
@RequestMapping("/api/v1/manager/media")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER')")
public class ManagerMediaController {

	private final CloudinaryUploadService cloudinaryUploadService;

	@GetMapping("/status")
	public ResponseEntity<ApiResponse<MediaUploadStatusDto>> status() {
		return ResponseEntity.ok(ApiResponse.success(MediaUploadStatusDto.builder()
				.enabled(cloudinaryUploadService.isUploadConfigured())
				.hybridUpload(true)
				.build()));
	}

	/** Chữ ký upload trực tiếp (ảnh quản lý: xe, showroom, …); URL lưu qua API nghiệp vụ hiện có. */
	@GetMapping("/upload-signature")
	public ResponseEntity<ApiResponse<CloudinarySignedUploadDto>> uploadSignature() {
		return ResponseEntity.ok(ApiResponse.success(
				cloudinaryUploadService.buildSignedDirectUpload(MediaUploadContext.MANAGER_GENERAL, null)));
	}
}
