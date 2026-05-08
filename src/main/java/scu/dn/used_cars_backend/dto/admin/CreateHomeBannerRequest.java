package scu.dn.used_cars_backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateHomeBannerRequest {

	@NotBlank
	@Size(max = 1000)
	private String imageUrl;

	@Size(max = 255)
	private String cloudinaryPublicId;
}
