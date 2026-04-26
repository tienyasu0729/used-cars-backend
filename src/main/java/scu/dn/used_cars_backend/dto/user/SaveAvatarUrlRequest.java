package scu.dn.used_cars_backend.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sau khi client upload lên Cloudinary, gửi {@code secure_url} để lưu {@code Users.avatar_url}.
 */
public record SaveAvatarUrlRequest(
		@NotBlank(message = "Thiếu URL ảnh.")
		@Size(max = 500, message = "URL quá dài.")
		String avatarUrl) {
}
