package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.admin.CreateHomeBannerRequest;
import scu.dn.used_cars_backend.dto.admin.HomeBannerAdminDto;
import scu.dn.used_cars_backend.dto.media.CloudinarySignedUploadDto;
import scu.dn.used_cars_backend.service.CloudinaryUploadService;
import scu.dn.used_cars_backend.service.HomePageBannerService;
import scu.dn.used_cars_backend.service.MediaUploadContext;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/home-banners")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminHomeBannerController {

	private final HomePageBannerService homePageBannerService;
	private final CloudinaryUploadService cloudinaryUploadService;

	@GetMapping("/upload-signature")
	public ResponseEntity<ApiResponse<CloudinarySignedUploadDto>> uploadSignature() {
		return ResponseEntity.ok(ApiResponse.success(
				cloudinaryUploadService.buildSignedDirectUpload(MediaUploadContext.HOME_BANNER, null)));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<HomeBannerAdminDto>>> list() {
		return ResponseEntity.ok(ApiResponse.success(homePageBannerService.listAllForAdmin()));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<HomeBannerAdminDto>> create(@Valid @RequestBody CreateHomeBannerRequest body) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(homePageBannerService.create(body)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable long id) {
		homePageBannerService.delete(id);
		return ResponseEntity.ok(ApiResponse.success(null));
	}
}
