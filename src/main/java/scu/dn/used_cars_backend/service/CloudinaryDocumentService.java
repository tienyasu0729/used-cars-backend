package scu.dn.used_cars_backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.config.CloudinaryDocumentProperties;

import java.io.IOException;
import java.util.Map;

/**
 * Service lưu trữ tài liệu (CCCD, Thu nhập, Hợp đồng...) lên Cloudinary riêng.
 * Backend nhận file MultipartFile và trực tiếp đẩy lên Cloudinary để lấy URL.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryDocumentService {

	private final CloudinaryDocumentProperties cloudinaryProperties;

	public boolean isUploadConfigured() {
		return cloudinaryProperties.uploadConfigured();
	}

	private Cloudinary client() {
		if (!isUploadConfigured()) {
			throw new BusinessException(ErrorCode.MEDIA_UPLOAD_NOT_CONFIGURED,
					"Máy chủ chưa cấu hình Cloudinary dành riêng cho Documents.");
		}
		Map<String, Object> config = ObjectUtils.asMap(
				"cloud_name", cloudinaryProperties.cloudName().trim(),
				"api_key", cloudinaryProperties.apiKey().trim(),
				"api_secret", cloudinaryProperties.apiSecret().trim());
		return new Cloudinary(config);
	}

	/**
	 * Upload file lên Cloudinary, trả về secure_url
	 */
	public String uploadDocument(MultipartFile file, MediaUploadContext context, Long applicationId) {
		if (file == null || file.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "File rỗng hoặc không tồn tại.");
		}
		
		Cloudinary c = client();
		try {
			Map<String, Object> uploadOptions = ObjectUtils.asMap(
					"folder", context.cloudinaryFolder() + "/" + applicationId,
					"resource_type", "auto" // Hỗ trợ pdf, image
			);

			@SuppressWarnings("unchecked")
			Map<String, Object> uploadResult = c.uploader().upload(file.getBytes(), uploadOptions);
			
			String secureUrl = (String) uploadResult.get("secure_url");
			if (secureUrl == null) {
				throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Không lấy được secure_url từ Cloudinary.");
			}
			return secureUrl;
		} catch (IOException e) {
			log.error("Lỗi đọc file document: {}", e.getMessage(), e);
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi đọc file tài liệu.");
		} catch (Exception e) {
			log.error("Lỗi upload file document lên Cloudinary: {}", e.getMessage(), e);
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi kết nối tới server lưu trữ tài liệu.");
		}
	}

	public String uploadBase64Image(String dataUrl, MediaUploadContext context, Long applicationId) {
		if (dataUrl == null || !dataUrl.startsWith("data:image")) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Dữ liệu hình ảnh không hợp lệ.");
		}

		Cloudinary c = client();
		try {
			Map<String, Object> uploadOptions = ObjectUtils.asMap(
					"folder", context.cloudinaryFolder() + "/" + applicationId,
					"resource_type", "image"
			);

			@SuppressWarnings("unchecked")
			Map<String, Object> uploadResult = c.uploader().upload(dataUrl, uploadOptions);

			String secureUrl = (String) uploadResult.get("secure_url");
			if (secureUrl == null) {
				throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Không lấy được secure_url từ Cloudinary.");
			}
			return secureUrl;
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.error("Lỗi upload base64 image lên Cloudinary: {}", e.getMessage(), e);
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi upload chữ ký lên server lưu trữ.");
		}
	}

	public void destroyDocumentByUrl(String secureUrl) {
		if (secureUrl == null || secureUrl.isBlank()) {
			return;
		}
		try {
			// Extract public_id from secure_url
			// Ví dụ: https://res.cloudinary.com/cloud_name/image/upload/v1234567/used-cars/installments/docs/1/abc.jpg
			// public_id = used-cars/installments/docs/1/abc
			String folderPath = MediaUploadContext.INSTALLMENT_DOCUMENT.cloudinaryFolder();
			int folderIndex = secureUrl.indexOf(folderPath);
			if (folderIndex == -1) {
				return; // Không thuộc folder quản lý
			}
			String afterUpload = secureUrl.substring(folderIndex);
			// Remove versioning or extension
			int lastDot = afterUpload.lastIndexOf('.');
			String publicId = lastDot != -1 ? afterUpload.substring(0, lastDot) : afterUpload;
			
			// Detect resource type
			String resourceType = secureUrl.contains("/raw/") ? "raw" : (secureUrl.endsWith(".pdf") ? "image" : "image");

			Cloudinary c = client();
			@SuppressWarnings("unchecked")
			Map<String, Object> res = c.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", resourceType));
			Object r = res.get("result");
			if (r != null && !"ok".equals(String.valueOf(r)) && !"not found".equalsIgnoreCase(String.valueOf(r))) {
				log.warn("Cloudinary document destroy unexpected result: {}", res);
			}
		} catch (Exception e) {
			log.warn("Cloudinary document destroy failed: {}", e.getMessage());
		}
	}
}
