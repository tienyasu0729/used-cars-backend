package scu.dn.used_cars_backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminCatalogLookupWriteRequest {

	@NotBlank
	@Size(max = 50)
	private String name;
}
