package scu.dn.used_cars_backend.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload {@code data} của {@code PUT /api/v1/users/me/avatar} sau khi lưu URL ảnh.
 * <p>
 * Luồng thực tế: client upload trực tiếp lên Cloudinary (xem {@code GET …/avatar/upload-signature}),
 * rồi gửi {@code secure_url} — khác spec Sprint 1 gốc (multipart lên server + lưu đĩa).
 * Giữ DTO tường minh thay vì {@code Map} để contract ổn định cho OpenAPI/FE.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvatarUploadResponse {

	/** URL ảnh đại diện (Cloudinary hoặc URL tuyệt đối đã chuẩn hóa). */
	private String avatarUrl;
}
