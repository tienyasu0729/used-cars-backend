package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.article.ArticleDetailResponse;
import scu.dn.used_cars_backend.dto.article.ArticleListItemResponse;
import scu.dn.used_cars_backend.dto.article.CreateArticleRequest;
import scu.dn.used_cars_backend.dto.article.UpdateArticleRequest;
import scu.dn.used_cars_backend.entity.Article;
import scu.dn.used_cars_backend.entity.ArticleCategory;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.repository.ArticleCategoryRepository;
import scu.dn.used_cars_backend.repository.ArticleRepository;
import scu.dn.used_cars_backend.repository.UserRepository;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ArticleService {

	private final ArticleRepository articleRepository;
	private final ArticleCategoryRepository categoryRepository;
	private final UserRepository userRepository;
	private final CloudinaryUploadService cloudinaryUploadService;

	@Transactional(readOnly = true)
	public Page<ArticleListItemResponse> getPublishedArticles(String keyword, String categorySlug, Pageable pageable) {
		Specification<Article> spec = Specification.where(notDeleted())
				.and(hasStatus("published"));

		if (keyword != null && !keyword.isBlank()) {
			spec = spec.and(titleOrSummaryContains(keyword.trim()));
		}
		if (categorySlug != null && !categorySlug.isBlank()) {
			spec = spec.and(hasCategorySlug(categorySlug.trim()));
		}

		return articleRepository.findAll(spec, pageable).map(this::toListItem);
	}

	@Transactional
	public ArticleDetailResponse getArticleBySlug(String slug) {
		Article article = articleRepository.findBySlugAndDeletedFalse(slug)
				.orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND, "Không tìm thấy bài viết."));

		if (!"published".equals(article.getStatus())) {
			throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND, "Bài viết không khả dụng.");
		}

		articleRepository.incrementViewCount(article.getId());
		return toDetail(article);
	}

	@Transactional(readOnly = true)
	public Page<ArticleListItemResponse> getAllArticlesForAdmin(String keyword, String status, Long categoryId,
			Long restrictToAuthorId, Pageable pageable) {
		Specification<Article> spec = Specification.where(notDeleted());

		if (restrictToAuthorId != null) {
			spec = spec.and(hasAuthorId(restrictToAuthorId));
		}
		if (keyword != null && !keyword.isBlank()) {
			spec = spec.and(titleOrSummaryContains(keyword.trim()));
		}
		if (status != null && !status.isBlank()) {
			spec = spec.and(hasStatus(status.trim()));
		}
		if (categoryId != null) {
			spec = spec.and(hasCategoryId(categoryId));
		}

		return articleRepository.findAll(spec, pageable).map(this::toListItem);
	}

	@Transactional(readOnly = true)
	public ArticleDetailResponse getArticleByIdForAdmin(Long id, Long currentUserId, boolean fullAdmin) {
		Article article = articleRepository.findById(id)
				.filter(a -> !a.isDeleted())
				.orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND, "Không tìm thấy bài viết."));
		assertArticleOwnerOrAdmin(article, currentUserId, fullAdmin);
		return toDetail(article);
	}

	@Transactional
	public ArticleDetailResponse createArticle(CreateArticleRequest req, Long authorId) {
		String slug = req.getSlug() != null && !req.getSlug().isBlank()
				? req.getSlug().trim()
				: ArticleCategoryService.toSlug(req.getTitle());

		if (articleRepository.existsBySlug(slug)) {
			throw new BusinessException(ErrorCode.ARTICLE_SLUG_CONFLICT, "Slug bài viết đã tồn tại.");
		}

		Article article = new Article();
		article.setTitle(req.getTitle().trim());
		article.setSlug(slug);
		article.setSummary(req.getSummary());
		article.setContent(req.getContent());
		assertArticleThumbnailIfPresent(req.getThumbnailUrl());
		article.setThumbnailUrl(req.getThumbnailUrl() != null ? req.getThumbnailUrl().trim() : null);

		if (req.getCategoryId() != null) {
			ArticleCategory cat = categoryRepository.findById(req.getCategoryId())
					.orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_CATEGORY_NOT_FOUND, "Danh mục không tồn tại."));
			article.setCategory(cat);
		}

		if (authorId != null) {
			User author = userRepository.findById(authorId).orElse(null);
			article.setAuthor(author);
		}

		String status = req.getStatus() != null ? req.getStatus() : "draft";
		article.setStatus(status);
		article.setFeatured(req.isFeatured());
		if ("published".equals(status)) {
			article.setPublishedAt(Instant.now());
		}

		articleRepository.save(article);
		return toDetail(article);
	}

	@Transactional
	public ArticleDetailResponse updateArticle(Long id, UpdateArticleRequest req, Long currentUserId, boolean fullAdmin) {
		Article article = articleRepository.findById(id)
				.filter(a -> !a.isDeleted())
				.orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND, "Không tìm thấy bài viết."));
		assertArticleOwnerOrAdmin(article, currentUserId, fullAdmin);

		if (req.getTitle() != null) {
			article.setTitle(req.getTitle().trim());
		}
		if (req.getSlug() != null && !req.getSlug().isBlank()) {
			String newSlug = req.getSlug().trim();
			if (!newSlug.equals(article.getSlug()) && articleRepository.existsBySlug(newSlug)) {
				throw new BusinessException(ErrorCode.ARTICLE_SLUG_CONFLICT, "Slug bài viết đã tồn tại.");
			}
			article.setSlug(newSlug);
		}
		if (req.getSummary() != null) {
			article.setSummary(req.getSummary());
		}
		if (req.getContent() != null) {
			article.setContent(req.getContent());
		}
		if (req.getThumbnailUrl() != null) {
			assertArticleThumbnailIfPresent(req.getThumbnailUrl());
			article.setThumbnailUrl(req.getThumbnailUrl().isBlank() ? null : req.getThumbnailUrl().trim());
		}
		if (req.getCategoryId() != null) {
			ArticleCategory cat = categoryRepository.findById(req.getCategoryId())
					.orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_CATEGORY_NOT_FOUND, "Danh mục không tồn tại."));
			article.setCategory(cat);
		}
		if (req.getStatus() != null) {
			String oldStatus = article.getStatus();
			article.setStatus(req.getStatus());
			if ("published".equals(req.getStatus()) && !"published".equals(oldStatus)) {
				article.setPublishedAt(Instant.now());
			}
		}
		if (req.getFeatured() != null) {
			article.setFeatured(req.getFeatured());
		}

		articleRepository.save(article);
		return toDetail(article);
	}

	@Transactional
	public void deleteArticle(Long id, Long currentUserId, boolean fullAdmin) {
		Article article = articleRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND, "Không tìm thấy bài viết."));
		assertArticleOwnerOrAdmin(article, currentUserId, fullAdmin);
		article.setDeleted(true);
		articleRepository.save(article);
	}

	@Transactional
	public ArticleDetailResponse toggleVisibility(Long id, Long currentUserId, boolean fullAdmin) {
		Article article = articleRepository.findById(id)
				.filter(a -> !a.isDeleted())
				.orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND, "Không tìm thấy bài viết."));
		assertArticleOwnerOrAdmin(article, currentUserId, fullAdmin);

		if ("published".equals(article.getStatus())) {
			article.setStatus("hidden");
		} else if ("hidden".equals(article.getStatus())) {
			article.setStatus("published");
			if (article.getPublishedAt() == null) {
				article.setPublishedAt(Instant.now());
			}
		} else {
			throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Chỉ ẩn/hiện bài viết đã xuất bản.");
		}

		articleRepository.save(article);
		return toDetail(article);
	}

	private ArticleListItemResponse toListItem(Article a) {
		return ArticleListItemResponse.builder()
				.id(a.getId())
				.title(a.getTitle())
				.slug(a.getSlug())
				.summary(a.getSummary())
				.thumbnailUrl(a.getThumbnailUrl())
				.authorName(a.getAuthor() != null ? a.getAuthor().getName() : null)
				.categoryName(a.getCategory() != null ? a.getCategory().getName() : null)
				.categorySlug(a.getCategory() != null ? a.getCategory().getSlug() : null)
				.status(a.getStatus())
				.featured(a.isFeatured())
				.publishedAt(a.getPublishedAt())
				.createdAt(a.getCreatedAt())
				.viewCount(a.getViewCount())
				.build();
	}

	private ArticleDetailResponse toDetail(Article a) {
		return ArticleDetailResponse.builder()
				.id(a.getId())
				.title(a.getTitle())
				.slug(a.getSlug())
				.summary(a.getSummary())
				.content(a.getContent())
				.thumbnailUrl(a.getThumbnailUrl())
				.authorName(a.getAuthor() != null ? a.getAuthor().getName() : null)
				.authorId(a.getAuthor() != null ? a.getAuthor().getId() : null)
				.categoryName(a.getCategory() != null ? a.getCategory().getName() : null)
				.categorySlug(a.getCategory() != null ? a.getCategory().getSlug() : null)
				.categoryId(a.getCategory() != null ? a.getCategory().getId() : null)
				.status(a.getStatus())
				.featured(a.isFeatured())
				.publishedAt(a.getPublishedAt())
				.createdAt(a.getCreatedAt())
				.updatedAt(a.getUpdatedAt())
				.viewCount(a.getViewCount())
				.build();
	}

	private static Specification<Article> notDeleted() {
		return (root, q, cb) -> cb.isFalse(root.get("deleted"));
	}

	private static Specification<Article> hasStatus(String status) {
		return (root, q, cb) -> cb.equal(root.get("status"), status);
	}

	private static Specification<Article> titleOrSummaryContains(String keyword) {
		return (root, q, cb) -> {
			String pattern = "%" + keyword.toLowerCase() + "%";
			return cb.or(
					cb.like(cb.lower(root.get("title")), pattern),
					cb.like(cb.lower(root.get("summary")), pattern));
		};
	}

	private static Specification<Article> hasCategorySlug(String slug) {
		return (root, q, cb) -> cb.equal(root.get("category").get("slug"), slug);
	}

	private static Specification<Article> hasCategoryId(Long categoryId) {
		return (root, q, cb) -> cb.equal(root.get("category").get("id"), categoryId);
	}

	private static Specification<Article> hasAuthorId(Long authorId) {
		return (root, q, cb) -> cb.equal(root.get("author").get("id"), authorId);
	}

	/** Non-admin (CMS) chỉ quản lý bài do chính họ tạo. */
	private static void assertArticleOwnerOrAdmin(Article article, Long currentUserId, boolean fullAdmin) {
		if (fullAdmin) {
			return;
		}
		if (article.getAuthor() == null || !article.getAuthor().getId().equals(currentUserId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền thao tác bài viết này.");
		}
	}

	/**
	 * Cho phép URL ngoài Cloudinary; nếu URL là res.cloudinary.com thì phải nằm đúng thư mục bài viết.
	 */
	private void assertArticleThumbnailIfPresent(String thumbnailUrl) {
		if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
			return;
		}
		String t = thumbnailUrl.trim();
		if (t.toLowerCase().contains("res.cloudinary.com")) {
			cloudinaryUploadService.assertSecureUrlMatchesSignedContext(t, MediaUploadContext.ARTICLE_THUMBNAIL, null);
		}
	}
}
