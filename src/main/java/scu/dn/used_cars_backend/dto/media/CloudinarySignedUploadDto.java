package scu.dn.used_cars_backend.dto.media;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tham số upload trực tiếp lên Cloudinary (client POST multipart tới {@link #uploadUrl}).
 * {@code api_secret} không bao giờ có trong DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudinarySignedUploadDto {

	private String cloudName;
	private String apiKey;
	private long timestamp;
	private String signature;
	/** Folder do server cố định — client không được đổi khi upload. */
	private String folder;
	/**
	 * {@code public_id} (không gồm folder) — null nghĩa là Cloudinary tự sinh (ảnh quản lý).
	 */
	private String publicId;
	/** Gửi kèm form nếu true (phải khớp lúc ký). */
	private boolean overwrite;
	private String uploadUrl;
	/** Luôn {@code image} trong phạm vi hiện tại. */
	private String resourceType;
}
