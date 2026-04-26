package scu.dn.used_cars_backend.dto.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDetailResponse {

	private Long id;
	private String title;
	private String slug;
	private String summary;
	private String content;
	private String thumbnailUrl;
	private String authorName;
	private Long authorId;
	private String categoryName;
	private String categorySlug;
	private Long categoryId;
	private String status;
	private Instant publishedAt;
	private Instant createdAt;
	private Instant updatedAt;
	private int viewCount;
}
