package scu.dn.used_cars_backend.dto.pricing;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ManagerPricingImageAssetRequest {

	@NotBlank
	private String url;

	private String publicId;
	private String source;

	@NotBlank
	private String declaredGroup;

	private String caption;
	private String captionBy;
	private String captionType;
}
