package scu.dn.used_cars_backend.dto.article;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateArticleRequest {

	@NotBlank
	@Size(max = 300)
	private String title;

	@Size(max = 350)
	private String slug;

	@Size(max = 500)
	private String summary;

	@NotBlank
	private String content;

	@Size(max = 500)
	private String thumbnailUrl;

	private Long categoryId;

	private String status = "draft";

	private boolean featured = false;
}
