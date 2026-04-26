package scu.dn.used_cars_backend.dto.article;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCategoryRequest {

	@NotBlank
	@Size(max = 100)
	private String name;

	@Size(max = 120)
	private String slug;

	@Size(max = 500)
	private String description;

	private int sortOrder = 0;

	private boolean active = true;
}
