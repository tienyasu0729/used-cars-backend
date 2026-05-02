package scu.dn.used_cars_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "Articles")
public class Article extends BaseEntity {

	@Column(nullable = false, length = 300)
	private String title;

	@Column(nullable = false, length = 350, unique = true)
	private String slug;

	@Column(length = 500)
	private String summary;

	@Column(nullable = false, columnDefinition = "NVARCHAR(MAX)")
	private String content;

	@Column(name = "thumbnail_url", length = 500)
	private String thumbnailUrl;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "author_id")
	private User author;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id")
	private ArticleCategory category;

	@Column(nullable = false, length = 20)
	private String status = "draft";

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "is_featured", nullable = false)
	private boolean featured = false;

	@Column(name = "view_count", nullable = false)
	private int viewCount = 0;

	@Column(name = "is_deleted", nullable = false)
	private boolean deleted = false;
}
