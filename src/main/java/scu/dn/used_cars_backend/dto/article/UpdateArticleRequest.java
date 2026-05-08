package scu.dn.used_cars_backend.dto.article;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateArticleRequest {

	@Size(max = 300)
	private String title;

	@Size(max = 350)
	private String slug;

	@Size(max = 500)
	private String summary;

	private String content;

	@Size(max = 500)
	private String thumbnailUrl;

	private Long categoryId;

	private String status;

	private Boolean featured;
}
