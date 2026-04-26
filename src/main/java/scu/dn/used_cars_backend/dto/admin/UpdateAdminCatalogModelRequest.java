package scu.dn.used_cars_backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAdminCatalogModelRequest {

	@NotBlank
	@Size(max = 200)
	private String name;

	@NotBlank
	@Pattern(regexp = "(?i)^(active|inactive)$")
	private String status;
}
