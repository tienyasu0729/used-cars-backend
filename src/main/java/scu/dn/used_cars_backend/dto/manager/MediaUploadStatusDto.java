package scu.dn.used_cars_backend.dto.manager;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadStatusDto {

	private boolean enabled;
	/** Upload trực tiếp Cloudinary (backend chỉ ký). */
	@Builder.Default
	private boolean hybridUpload = true;
}
