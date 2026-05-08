package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.article.ArticleCategoryResponse;
import scu.dn.used_cars_backend.dto.article.ArticleDetailResponse;
import scu.dn.used_cars_backend.dto.article.ArticleListItemResponse;
import scu.dn.used_cars_backend.dto.article.CreateArticleRequest;
import scu.dn.used_cars_backend.dto.article.CreateCategoryRequest;
import scu.dn.used_cars_backend.dto.article.UpdateArticleRequest;
import scu.dn.used_cars_backend.dto.media.CloudinarySignedUploadDto;
import scu.dn.used_cars_backend.dto.vehicle.PageMetaDto;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.security.JwtRoleNames;
import scu.dn.used_cars_backend.service.ArticleCategoryService;
import scu.dn.used_cars_backend.service.ArticleService;
import scu.dn.used_cars_backend.service.CloudinaryUploadService;
import scu.dn.used_cars_backend.service.MediaUploadContext;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/articles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasAuthority('PERMISSION_CMS_MANAGE')")
public class AdminArticleController {

	private final ArticleService articleService;
	private final ArticleCategoryService categoryService;
	private final CloudinaryUploadService cloudinaryUploadService;

	@GetMapping("/upload-signature")
	public ResponseEntity<ApiResponse<CloudinarySignedUploadDto>> articleThumbnailUploadSignature() {
		return ResponseEntity.ok(ApiResponse.success(
				cloudinaryUploadService.buildSignedDirectUpload(MediaUploadContext.ARTICLE_THUMBNAIL, null)));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<ArticleListItemResponse>>> list(
			Authentication authentication,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) Long categoryId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {

		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean fullAdmin = JwtRoleNames.isAdmin(authentication);
		Long restrictAuthor = fullAdmin ? null : userId;

		Page<ArticleListItemResponse> result = articleService.getAllArticlesForAdmin(
				keyword, status, categoryId, restrictAuthor,
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

		PageMetaDto meta = PageMetaDto.builder()
				.page(result.getNumber())
				.size(result.getSize())
				.totalElements(result.getTotalElements())
				.totalPages(result.getTotalPages())
				.build();

		return ResponseEntity.ok(ApiResponse.success(result.getContent(), meta));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<ArticleDetailResponse>> detail(
			@PathVariable Long id, Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean fullAdmin = JwtRoleNames.isAdmin(authentication);
		return ResponseEntity.ok(ApiResponse.success(articleService.getArticleByIdForAdmin(id, userId, fullAdmin)));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<ArticleDetailResponse>> create(
			@Valid @RequestBody CreateArticleRequest body,
			Authentication authentication) {
		Long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(articleService.createArticle(body, userId)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<ArticleDetailResponse>> update(
			@PathVariable Long id,
			@Valid @RequestBody UpdateArticleRequest body,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean fullAdmin = JwtRoleNames.isAdmin(authentication);
		return ResponseEntity.ok(ApiResponse.success(articleService.updateArticle(id, body, userId, fullAdmin)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean fullAdmin = JwtRoleNames.isAdmin(authentication);
		articleService.deleteArticle(id, userId, fullAdmin);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@PatchMapping("/{id}/toggle-visibility")
	public ResponseEntity<ApiResponse<ArticleDetailResponse>> toggleVisibility(
			@PathVariable Long id, Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean fullAdmin = JwtRoleNames.isAdmin(authentication);
		return ResponseEntity.ok(ApiResponse.success(articleService.toggleVisibility(id, userId, fullAdmin)));
	}

	// --- Category management ---

	@GetMapping("/categories")
	public ResponseEntity<ApiResponse<List<ArticleCategoryResponse>>> listCategories() {
		return ResponseEntity.ok(ApiResponse.success(categoryService.listAllCategories()));
	}

	@PostMapping("/categories")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<ArticleCategoryResponse>> createCategory(
			@Valid @RequestBody CreateCategoryRequest body) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(categoryService.create(body)));
	}

	@PutMapping("/categories/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<ArticleCategoryResponse>> updateCategory(
			@PathVariable Long id,
			@Valid @RequestBody CreateCategoryRequest body) {
		return ResponseEntity.ok(ApiResponse.success(categoryService.update(id, body)));
	}

	@DeleteMapping("/categories/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
		categoryService.delete(id);
		return ResponseEntity.ok(ApiResponse.success(null));
	}
}
