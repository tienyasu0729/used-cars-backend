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
public class ArticleListItemResponse {

	private Long id;
	private String title;
	private String slug;
	private String summary;
	private String thumbnailUrl;
	private String authorName;
	private String categoryName;
	private String categorySlug;
	private String status;
	private boolean featured;
	private Instant publishedAt;
	private Instant createdAt;
	private int viewCount;
}
