package scu.dn.used_cars_backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.config.CloudinaryProperties;
import scu.dn.used_cars_backend.dto.media.CloudinarySignedUploadDto;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Hybrid upload: backend chỉ ký tham số và xác thực {@code secure_url}; không nhận file qua server.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryUploadService {

	private final CloudinaryProperties cloudinary;

	public boolean isUploadConfigured() {
		return cloudinary.uploadConfigured();
	}

	private Cloudinary client() {
		if (!isUploadConfigured()) {
			throw new BusinessException(ErrorCode.MEDIA_UPLOAD_NOT_CONFIGURED,
					"Máy chủ chưa cấu hình upload ảnh (Cloudinary: cloud-name, api-key, api-secret).");
		}
		Map<String, Object> config = ObjectUtils.asMap(
				"cloud_name", cloudinary.cloudName().trim(),
				"api_key", cloudinary.apiKey().trim(),
				"api_secret", cloudinary.apiSecret().trim());
		return new Cloudinary(config);
	}

	/**
	 * Sinh chữ ký upload trực tiếp; {@code scopeId} bắt buộc với {@link MediaUploadContext#AVATAR}.
	 */
	public CloudinarySignedUploadDto buildSignedDirectUpload(MediaUploadContext context, Long scopeId) {
		if (context.requiresScopeId() && (scopeId == null || scopeId < 1)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thiếu tham số phạm vi upload.");
		}
		Cloudinary c = client();
		long timestamp = System.currentTimeMillis() / 1000;
		Map<String, Object> toSign = new HashMap<>();
		toSign.put("timestamp", timestamp);
		toSign.put("folder", context.cloudinaryFolder());
		String publicId = null;
		boolean overwrite = false;
		if (context == MediaUploadContext.AVATAR) {
			publicId = "user_" + scopeId;
			overwrite = true;
			toSign.put("public_id", publicId);
			toSign.put("overwrite", true);
		}
		String signature = c.apiSignRequest(toSign, cloudinary.apiSecret().trim());
		String cn = cloudinary.cloudName().trim();
		return CloudinarySignedUploadDto.builder()
				.cloudName(cn)
				.apiKey(cloudinary.apiKey().trim())
				.timestamp(timestamp)
				.signature(signature)
				.folder(context.cloudinaryFolder())
				.publicId(publicId)
				.overwrite(overwrite)
				.uploadUrl("https://api.cloudinary.com/v1_1/" + cn + "/image/upload")
				.resourceType("image")
				.build();
	}

	/**
	 * Kiểm tra {@code secure_url} trả về từ Cloudinary sau upload trực tiếp — đúng cloud, đúng folder/public_id.
	 */
	public void assertSecureUrlMatchesSignedContext(String secureUrl, MediaUploadContext context, Long scopeId) {
		if (secureUrl == null || secureUrl.isBlank()) {
			throw new BusinessException(ErrorCode.CLOUDINARY_URL_INVALID, "URL ảnh không hợp lệ.");
		}
		if (context.requiresScopeId() && (scopeId == null || scopeId < 1)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thiếu tham số phạm vi.");
		}
		final URI uri;
		try {
			uri = URI.create(secureUrl.trim());
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.CLOUDINARY_URL_INVALID, "URL ảnh không hợp lệ.");
		}
		if (!"https".equalsIgnoreCase(uri.getScheme())) {
			throw new BusinessException(ErrorCode.CLOUDINARY_URL_INVALID, "Chỉ chấp nhận URL HTTPS.");
		}
		String host = uri.getHost();
		if (host == null || !host.equalsIgnoreCase("res.cloudinary.com")) {
			throw new BusinessException(ErrorCode.CLOUDINARY_URL_INVALID, "URL phải thuộc res.cloudinary.com.");
		}
		String cloud = cloudinary.cloudName().trim();
		String path = uri.getPath();
		if (path == null) {
			throw new BusinessException(ErrorCode.CLOUDINARY_URL_INVALID, "URL ảnh không hợp lệ.");
		}
		String prefix = "/" + cloud + "/image/upload/";
		if (!path.startsWith(prefix)) {
			throw new BusinessException(ErrorCode.CLOUDINARY_URL_INVALID, "URL không thuộc Cloudinary cloud này.");
		}
		String afterUpload = path.substring(prefix.length());
		String folder = context.cloudinaryFolder();
		int folderAt = afterUpload.indexOf(folder + "/");
		if (folderAt < 0) {
			throw new BusinessException(ErrorCode.CLOUDINARY_URL_INVALID, "URL không nằm đúng thư mục đã cấp phép.");
		}
		String assetPath = afterUpload.substring(folderAt);
		if (context == MediaUploadContext.AVATAR) {
			String expected = folder + "/user_" + scopeId;
			if (!assetPath.equals(expected) && !assetPath.startsWith(expected + ".")) {
				throw new BusinessException(ErrorCode.CLOUDINARY_URL_INVALID, "Avatar không khớp tài khoản.");
			}
		}
	}
}
